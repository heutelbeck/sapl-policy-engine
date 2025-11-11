/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler.operators;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.Function;

/**
 * Schema validation utilities for SAPL policy compilation.
 * <p>
 * Provides JSON Schema 2020-12 validation for type checking.
 */
@UtilityClass
public class SchemaValidation {

    private static final JsonSchemaFactory SCHEMA_FACTORY = configureSchemaFactoryBuilder().build();

    private static JsonSchemaFactory.Builder configureSchemaFactoryBuilder() {
        return JsonSchemaFactory.builder().metaSchema(JsonMetaSchema.getV202012())
                .defaultMetaSchemaIri(JsonMetaSchema.getV202012().getIri());
    }

    /**
     * Creates a reusable validator function from a JSON Schema.
     * <p>
     * The schema is compiled once and the returned function validates values.
     * Returns boolean validity only.
     * <p>
     * Example for access control validation:
     *
     * <pre>{@code
     * Value schema = Value.ofObject(Map.of("type", Value.of("object"), "properties",
     *         Value.ofObject(Map.of("clearanceLevel",
     *                 Value.ofObject(
     *                         Map.of("type", Value.of("number"), "minimum", Value.of(1), "maximum", Value.of(5))))),
     *         "required", Value.ofArray(Value.of("clearanceLevel"))));
     *
     * Function<Value, Value> validator = schemaValidatorFromSchema(schema);
     *
     * Value subject = Value.ofObject(Map.of("clearanceLevel", Value.of(3)));
     * Value result = validator.apply(subject); // Value.of(true)
     * }</pre>
     *
     * @param schema the JSON Schema 2020-12 as Value (typically ObjectValue)
     * @return validator function returning Value.of(true) if valid, Value.of(false)
     * if invalid,
     * or Value.error() if schema compilation or validation fails
     */
    public static Function<Value, Value> schemaValidatorFromSchema(Value schema) {
        try {
            val validator = SCHEMA_FACTORY.getSchema(ValueJsonMarshaller.toJsonNode(schema));
            return validationSubject -> {
                try {
                    val messages = validator.validate(ValueJsonMarshaller.toJsonNode(validationSubject));
                    return Value.of(messages.isEmpty());
                } catch (JsonSchemaException | IllegalArgumentException e) {
                    return Value.error(e);
                }
            };
        } catch (JsonSchemaException | IllegalArgumentException e) {
            return validationSubject -> Value.error(e);
        }
    }
}
