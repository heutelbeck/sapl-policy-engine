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

import java.util.Locale;
import java.util.Set;

import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Best-effort recursive redaction of well-known credential field names from a
 * serialized subscription element (subject, action, arguments). Fields named
 * like a credential are removed at any node depth, matched case-insensitively
 * on
 * the exact field name, so nested raw tokens (for example an OIDC
 * {@code principal.idToken.tokenValue} or OAuth2 {@code accessToken}) are
 * neutralised while benign domain fields are preserved.
 * <p>
 * Always applied to the default projections. A caller that genuinely needs a
 * credential in the subscription must shape it explicitly: a method-security
 * subject expression (which short-circuits the default projection), or a custom
 * {@code AuthorizationSubscriptionFactory}.
 */
public final class CredentialRedaction {

    private static final Set<String> CREDENTIAL_FIELD_NAMES = Set.of("accesstoken", "apikey", "authorization",
            "clientsecret", "cookie", "credentials", "idtoken", "password", "privatekey", "proxy-authorization",
            "refreshtoken", "salt", "secret", "set-cookie", "tokenvalue");

    // The request serializers emit a parsed cookies array of {name, value}
    // objects alongside the raw Cookie header. The header is stripped by name
    // above, but each cookie's token survives under the generic field "value",
    // which is too common to blanket-strip. Strip it only within the cookies array.
    private static final String COOKIES_FIELD      = "cookies";
    private static final String COOKIE_VALUE_FIELD = "value";

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
                    .filter(name -> CREDENTIAL_FIELD_NAMES.contains(name.toLowerCase(Locale.ROOT))).toList();
            objectNode.remove(credentialFields);
            redactCookieValues(objectNode);
            objectNode.values().forEach(CredentialRedaction::redact);
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
}
