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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

import java.io.IOException;
import java.util.ArrayList;

import lombok.val;

/**
 * Jackson deserializer for AuthorizationDecision.
 */
public class AuthorizationDecisionDeserializer extends JsonDeserializer<AuthorizationDecision> {

    private final ValueDeserializer valueDeserializer = new ValueDeserializer();

    @Override
    public AuthorizationDecision deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT for AuthorizationDecision.");
        }

        Decision   decision    = null;
        ArrayValue obligations = Value.EMPTY_ARRAY;
        ArrayValue advice      = Value.EMPTY_ARRAY;
        Value      resource    = Value.UNDEFINED;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "decision"    -> decision = Decision.valueOf(parser.getText());
            case "obligations" -> obligations = deserializeArrayValue(parser, context);
            case "advice"      -> advice = deserializeArrayValue(parser, context);
            case "resource"    -> resource = valueDeserializer.deserialize(parser, context);
            default            -> parser.skipChildren();
            }
        }

        if (decision == null) {
            throw new IOException("AuthorizationDecision requires decision field.");
        }

        return new AuthorizationDecision(decision, obligations, advice, resource);
    }

    private ArrayValue deserializeArrayValue(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected START_ARRAY for obligations/advice.");
        }

        val values = new ArrayList<Value>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(valueDeserializer.deserialize(parser, context));
        }
        return new ArrayValue(values);
    }
}
