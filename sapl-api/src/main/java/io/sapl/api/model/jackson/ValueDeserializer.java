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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;

import java.io.IOException;
import java.util.ArrayList;

import lombok.val;

/**
 * Jackson deserializer for the SAPL Value hierarchy.
 * <p>
 * Deserialization mapping:
 * <ul>
 * <li>JSON null maps to Value.NULL</li>
 * <li>JSON boolean maps to BooleanValue</li>
 * <li>JSON number maps to NumberValue</li>
 * <li>JSON string maps to TextValue</li>
 * <li>JSON array maps to ArrayValue</li>
 * <li>JSON object maps to ObjectValue</li>
 * </ul>
 * <p>
 * Note: UndefinedValue and ErrorValue cannot be deserialized from JSON as they
 * have no JSON representation.
 */
public class ValueDeserializer extends JsonDeserializer<Value> {

    @Override
    public Value deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return deserializeValue(parser);
    }

    @Override
    public Value getNullValue(DeserializationContext context) {
        return Value.NULL;
    }

    private Value deserializeValue(JsonParser parser) throws IOException {
        val token = parser.currentToken();
        return switch (token) {
        case VALUE_NULL                           -> Value.NULL;
        case VALUE_TRUE                           -> Value.TRUE;
        case VALUE_FALSE                          -> Value.FALSE;
        case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> Value.of(parser.getDecimalValue());
        case VALUE_STRING                         -> Value.of(parser.getText());
        case START_ARRAY                          -> deserializeArray(parser);
        case START_OBJECT                         -> deserializeObject(parser);
        default                                   -> throw new IOException("Unexpected JSON token: " + token);
        };
    }

    private Value deserializeArray(JsonParser parser) throws IOException {
        val elements = new ArrayList<Value>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(deserializeValue(parser));
        }
        return Value.ofArray(elements);
    }

    private Value deserializeObject(JsonParser parser) throws IOException {
        val builder = ObjectValue.builder();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();
            builder.put(fieldName, deserializeValue(parser));
        }
        return builder.build();
    }
}
