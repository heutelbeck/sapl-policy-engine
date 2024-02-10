/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import java.io.IOException;
import java.lang.reflect.Method;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaLoadingUtil {
    static final String ERROR_LOADING_SCHEMA_FROM_RESOURCES = "Error loading schema from resources.";
    static final String INVALID_SCHEMA_DEFINITION           = "Invalid schema definition for attribute found. This only validated JSON syntax, not compliance with JSONSchema specification";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode loadSchemaFromString(String attributeSchema) throws InitializationException {
        try {
            return MAPPER.readValue(attributeSchema, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new InitializationException(e, INVALID_SCHEMA_DEFINITION);
        }
    }

    public static JsonNode loadSchemaFromResource(Method method, String attributePathToSchema)
            throws InitializationException {
        try (var is = method.getDeclaringClass().getClassLoader().getResourceAsStream(attributePathToSchema)) {
            if (is == null) {
                throw new IOException("Schema file not found " + attributePathToSchema);
            }
            return MAPPER.readValue(is, JsonNode.class);
        } catch (IOException e) {
            throw new InitializationException(e, ERROR_LOADING_SCHEMA_FROM_RESOURCES);
        }
    }

}
