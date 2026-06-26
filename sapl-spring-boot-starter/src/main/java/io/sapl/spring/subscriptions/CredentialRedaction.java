/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.subscriptions;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Best-effort recursive redaction of well-known credential field names from a
 * serialized subscription element (subject, action, arguments). Fields named
 * like a credential are removed at any node depth, matched case-insensitively
 * and separator-insensitively (hyphens and underscores are ignored, so
 * {@code access_token}, {@code access-token} and {@code accessToken} all
 * match),
 * so nested raw tokens (for example an OIDC
 * {@code principal.idToken.tokenValue} or OAuth2 {@code accessToken}) are
 * neutralised while benign domain fields are preserved. A credential carried in
 * the raw HTTP query string is removed too, from the {@code query},
 * {@code queryParameters}, and {@code url} fields.
 * <p>
 * Always applied to the default projections. A caller that genuinely needs a
 * credential in the subscription must shape it explicitly: a method-security
 * subject expression (which short-circuits the default projection), or a custom
 * {@code AuthorizationSubscriptionFactory}.
 */
public final class CredentialRedaction {

    // Stored normalized to lowercase with no hyphens or underscores. Candidate
    // names are normalized the same way.
    private static final Set<String> CREDENTIAL_FIELD_NAMES = Set.of("accesstoken", "apikey", "authorization",
            "clientsecret", "cookie", "credentials", "idtoken", "password", "privatekey", "proxyauthorization",
            "refreshtoken", "salt", "secret", "setcookie", "tokenvalue");

    // Cookie tokens survive under the generic "value" field, so strip it only
    // inside the cookies array.
    private static final String COOKIES_FIELD      = "cookies";
    private static final String COOKIE_VALUE_FIELD = "value";

    // The raw query also rides in the string-valued "query" and "url" fields, out
    // of reach of field-name redaction.
    private static final String QUERY_FIELD            = "query";
    private static final String QUERY_PARAMETERS_FIELD = "queryParameters";
    private static final String URL_FIELD              = "url";

    private CredentialRedaction() {
    }

    /**
     * Removes credential-named fields from {@code node} recursively, in place.
     *
     * @param node the serialized node to redact (mutated)
     * @return the same node, for chaining
     */
    public static JsonNode redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            val credentialFields = objectNode.propertyNames().stream()
                    .filter(name -> CREDENTIAL_FIELD_NAMES.contains(normalize(name))).toList();
            objectNode.remove(credentialFields);
            redactCookieValues(objectNode);
            objectNode.values().forEach(CredentialRedaction::redact);
            // after the recursion above has redacted queryParameters
            redactQueryString(objectNode);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.values().forEach(CredentialRedaction::redact);
        }
        return node;
    }

    private static void redactCookieValues(ObjectNode objectNode) {
        if (objectNode.get(COOKIES_FIELD) instanceof ArrayNode cookies) {
            for (val cookie : cookies) {
                if (cookie instanceof ObjectNode cookieObject) {
                    cookieObject.remove(COOKIE_VALUE_FIELD);
                }
            }
        }
    }

    private static void redactQueryString(ObjectNode objectNode) {
        val queryNode = objectNode.get(QUERY_FIELD);
        val urlNode   = objectNode.get(URL_FIELD);
        val rawQuery  = rawQueryOf(queryNode, urlNode);
        if (!queryCarriesCredential(rawQuery)) {
            return;
        }
        // Rebuild "query" and the url's query from the already-redacted parameters. An
        // empty rebuild drops it, failing closed.
        val rebuilt = objectNode.get(QUERY_PARAMETERS_FIELD) instanceof ObjectNode parameters ? rebuildQuery(parameters)
                : "";
        if (queryNode instanceof StringNode) {
            if (rebuilt.isEmpty()) {
                objectNode.remove(QUERY_FIELD);
            } else {
                objectNode.put(QUERY_FIELD, rebuilt);
            }
        }
        if (urlNode instanceof StringNode url) {
            objectNode.put(URL_FIELD, rewriteUrlQuery(url.asString(), rebuilt));
        }
    }

    private static String rawQueryOf(JsonNode queryNode, JsonNode urlNode) {
        if (queryNode instanceof StringNode query) {
            return query.asString();
        }
        if (urlNode instanceof StringNode url) {
            return queryPartOf(url.asString());
        }
        return "";
    }

    private static String queryPartOf(String url) {
        val mark = url.indexOf('?');
        return mark < 0 ? "" : url.substring(mark + 1);
    }

    private static String rewriteUrlQuery(String url, String rebuiltQuery) {
        val mark     = url.indexOf('?');
        val basePart = mark < 0 ? url : url.substring(0, mark);
        return rebuiltQuery.isEmpty() ? basePart : basePart + '?' + rebuiltQuery;
    }

    private static boolean queryCarriesCredential(String rawQuery) {
        if (rawQuery.isBlank()) {
            return false;
        }
        for (val pair : rawQuery.split("&")) {
            val separator = pair.indexOf('=');
            val rawName   = separator < 0 ? pair : pair.substring(0, separator);
            if (CREDENTIAL_FIELD_NAMES.contains(normalize(safeUrlDecode(rawName)))) {
                return true;
            }
        }
        return false;
    }

    private static String rebuildQuery(ObjectNode parameters) {
        val query = new StringBuilder();
        for (val entry : parameters.properties()) {
            if (entry.getValue() instanceof ArrayNode values) {
                for (val value : values) {
                    appendParameter(query, entry.getKey(), value.asString());
                }
            } else {
                appendParameter(query, entry.getKey(), entry.getValue().asString());
            }
        }
        return query.toString();
    }

    private static void appendParameter(StringBuilder query, String name, String value) {
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String safeUrlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed percent-escape. Mirror the serializer's guarded decode and keep the
            // raw name.
            return value;
        }
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
