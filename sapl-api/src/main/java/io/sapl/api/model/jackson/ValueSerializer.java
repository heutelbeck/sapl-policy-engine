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

import io.sapl.api.model.*;

import java.math.BigDecimal;

import lombok.val;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for the SAPL Value hierarchy.
 * <p>
 * Serialization rules:
 * <ul>
 * <li>NullValue serializes to JSON null</li>
 * <li>BooleanValue serializes to JSON boolean</li>
 * <li>NumberValue serializes to JSON number</li>
 * <li>TextValue serializes to JSON string</li>
 * <li>ArrayValue serializes to JSON array</li>
 * <li>ObjectValue serializes to JSON object</li>
 * <li>UndefinedValue throws IllegalArgumentException</li>
 * <li>ErrorValue throws IllegalArgumentException (errors must not cross wire
 * boundaries)</li>
 * </ul>
 */
public class ValueSerializer extends StdSerializer<Value> {

    private static final String ERROR_CANNOT_SERIALIZE           = "Cannot serialize ErrorValue to JSON: ";
    private static final String ERROR_CANNOT_SERIALIZE_UNDEFINED = "Cannot serialize UndefinedValue to JSON.";

    public ValueSerializer() {
        super(Value.class);
    }

    @Override
    public void serialize(Value value, JsonGenerator generator, SerializationContext serializers) {
        serializeValue(value, generator);
    }

    private void serializeValue(Value value, JsonGenerator generator) {
        switch (value) {
        case NullValue ignored                   -> generator.writeNull();
        case BooleanValue(boolean booleanValue)  -> generator.writeBoolean(booleanValue);
        case NumberValue(BigDecimal numberValue) -> generator.writeNumber(numberValue);
        case TextValue(String textValue)         -> generator.writeString(textValue);
        case ArrayValue arrayValue               -> serializeArray(arrayValue, generator);
        case ObjectValue objectValue             -> serializeObject(objectValue, generator);
        case UndefinedValue ignored              ->
            throw new IllegalArgumentException(ERROR_CANNOT_SERIALIZE_UNDEFINED);
        case ErrorValue errorValue               ->
            throw new IllegalArgumentException(ERROR_CANNOT_SERIALIZE + errorValue.message());
        }
    }

    private void serializeArray(ArrayValue array, JsonGenerator generator) {
        generator.writeStartArray();
        for (Value element : array) {
            serializeValue(element, generator);
        }
        generator.writeEndArray();
    }

    private void serializeObject(ObjectValue object, JsonGenerator generator) {
        generator.writeStartObject();
        for (val entry : object.entrySet()) {
            generator.writeName(entry.getKey());
            serializeValue(entry.getValue(), generator);
        }
        generator.writeEndObject();
    }
}
