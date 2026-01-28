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

import io.sapl.api.pdp.MultiAuthorizationDecision;
import lombok.val;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for MultiAuthorizationDecision.
 * <p>
 * Deserializes from a JSON object mapping subscription IDs to their decisions.
 */
public class MultiAuthorizationDecisionDeserializer extends StdDeserializer<MultiAuthorizationDecision> {

    /**
     * Default constructor required by Jackson 3.
     */
    public MultiAuthorizationDecisionDeserializer() {
        super(MultiAuthorizationDecision.class);
    }

    private static final String ERROR_EXPECTED_START_OBJECT = "Expected START_OBJECT for MultiAuthorizationDecision.";

    private final AuthorizationDecisionDeserializer decisionDeserializer = new AuthorizationDecisionDeserializer();

    @Override
    public MultiAuthorizationDecision deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(MultiAuthorizationDecision.class, ERROR_EXPECTED_START_OBJECT);
        }

        val multiDecision = new MultiAuthorizationDecision();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val subscriptionId = parser.currentName();
            parser.nextToken();
            val decision = decisionDeserializer.deserialize(parser, context);
            multiDecision.setDecision(subscriptionId, decision);
        }

        return multiDecision;
    }
}
