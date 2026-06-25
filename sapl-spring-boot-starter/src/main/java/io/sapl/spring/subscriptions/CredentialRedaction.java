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
 * the raw HTTP query string is removed too: when a credential-named parameter
 * is
 * present the query is rebuilt from the already-redacted parsed parameters.
 * <p>
 * Always applied to the default projections. A caller that genuinely needs a
 * credential in the subscription must shape it explicitly: a method-security
 * subject expression (which short-circuits the default projection), or a custom
 * {@code AuthorizationSubscriptionFactory}.
 */
public final class CredentialRedaction {

    // Stored already normalized (lowercase, no hyphens/underscores). Candidate
    // field names are normalized the same way before lookup, so access_token,
    // access-token and accessToken all match.
    private static final Set<String> CREDENTIAL_FIELD_NAMES = Set.of("accesstoken", "apikey", "authorization",
            "clientsecret", "cookie", "credentials", "idtoken", "password", "privatekey", "proxyauthorization",
            "refreshtoken", "salt", "secret", "setcookie", "tokenvalue");

    // The request serializers emit a parsed cookies array of {name, value}
    // objects alongside the raw Cookie header. The header is stripped by name
    // above, but each cookie's token survives under the generic field "value",
    // which is too common to blanket-strip. Strip it only within the cookies array.
    private static final String COOKIES_FIELD      = "cookies";
    private static final String COOKIE_VALUE_FIELD = "value";

    // The request serializers emit the raw query string alongside the parsed
    // queryParameters. Field-name redaction reaches the parsed parameters but not
    // a credential embedded in the raw query string, so it is handled separately.
    private static final String QUERY_FIELD            = "query";
    private static final String QUERY_PARAMETERS_FIELD = "queryParameters";

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
            // After the children (including queryParameters) are redacted.
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
        if (!(objectNode.get(QUERY_FIELD) instanceof StringNode queryNode)
                || !queryCarriesCredential(queryNode.asString())) {
            return;
        }
        // A credential-named parameter is present in the raw query string, which the
        // name-based field redaction cannot reach inside a plain string value. Rebuild
        // the query from the already-redacted parsed parameters so the stripped
        // parameter cannot survive here either. Fail closed by dropping the raw query
        // if the parsed parameters are unavailable.
        if (objectNode.get(QUERY_PARAMETERS_FIELD) instanceof ObjectNode parameters) {
            objectNode.put(QUERY_FIELD, rebuildQuery(parameters));
        } else {
            objectNode.remove(QUERY_FIELD);
        }
    }

    private static boolean queryCarriesCredential(String rawQuery) {
        if (rawQuery.isBlank()) {
            return false;
        }
        for (val pair : rawQuery.split("&")) {
            val separator = pair.indexOf('=');
            val rawName   = separator < 0 ? pair : pair.substring(0, separator);
            if (CREDENTIAL_FIELD_NAMES.contains(normalize(URLDecoder.decode(rawName, StandardCharsets.UTF_8)))) {
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

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
