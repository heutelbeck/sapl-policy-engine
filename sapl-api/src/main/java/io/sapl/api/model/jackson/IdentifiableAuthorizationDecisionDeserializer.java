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
package io.sapl.api.model.jackson;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import lombok.val;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for IdentifiableAuthorizationDecision.
 */
public class IdentifiableAuthorizationDecisionDeserializer extends StdDeserializer<IdentifiableAuthorizationDecision> {

    /**
     * Default constructor required by Jackson 3.
     */
    public IdentifiableAuthorizationDecisionDeserializer() {
        super(IdentifiableAuthorizationDecision.class);
    }

    private static final String ERROR_EXPECTED_START_OBJECT   = "Expected START_OBJECT for IdentifiableAuthorizationDecision.";
    private static final String ERROR_MISSING_REQUIRED_FIELDS = "IdentifiableAuthorizationDecision requires subscriptionId and decision fields.";

    private final AuthorizationDecisionDeserializer decisionDeserializer = new AuthorizationDecisionDeserializer();

    @Override
    public IdentifiableAuthorizationDecision deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(IdentifiableAuthorizationDecision.class, ERROR_EXPECTED_START_OBJECT);
        }

        String                subscriptionId = null;
        AuthorizationDecision decision       = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "subscriptionId" -> subscriptionId = parser.getString();
            case "decision"       -> decision = decisionDeserializer.deserialize(parser, context);
            default               -> parser.skipChildren();
            }
        }

        if (subscriptionId == null || decision == null) {
            context.reportInputMismatch(IdentifiableAuthorizationDecision.class, ERROR_MISSING_REQUIRED_FIELDS);
        }

        return new IdentifiableAuthorizationDecision(subscriptionId, decision);
    }
}
