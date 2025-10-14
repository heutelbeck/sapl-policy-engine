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
package io.sapl.functions;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.resource.MapSchemaLoader;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import lombok.experimental.UtilityClass;

import java.util.HashMap;

@UtilityClass
@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION)
public class SchemaValidationLibrary {

    public static final String NAME        = "jsonschema";
    public static final String DESCRIPTION = "This library contains the functions for testing the compliance of a value with a JSON schema.";

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String ID = "$id";

    @Function(docs = """
            ```isCompliantWithSchema(validationSubject, OBJECT schema)```:
            This function tests the ```validationSubject``` for compliance with the with the provided JSON schema
            ```schema```.
            The schema itself cannot be validated and improper schema definitions may lead to unexpected results.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```
            policy "example"
            permit
            where
              var jsonSchema = {
                                 "type": "boolean"
                               };
              jsonschema.isCompliant(true, jsonSchema) == true;
              jsonschema.isCompliant(123, jsonSchema) == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isCompliant(Val validationSubject, @JsonObject Val jsonSchema) {
        return isCompliantWithExternalSchemas(validationSubject, jsonSchema, Val.ofEmptyArray());
    }

    @Function(docs = """
            ```isCompliantWithSchema(validationSubject, OBJECT jsonSchema, ARRAY externalSchemas)```:
            This function tests the ```validationSubject``` for compliance with the with the provided JSON schema
            ```schema```.
            The schema itself cannot be validated and improper schema definitions may lead to unexcpected results.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.
            If the ```jsonSchema``` contains external references to other ```schemas```, the validation function
            looks up the schemas in ```externalSchemas``` based on explicitly defined ```$id``` field in the schemas.
            If no $id field is provided, the schema will not be detectable.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```
            policy "example"
            permit
            where
              var externals = {
                    "$id": "https://example.com/coordinates",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Coordinates",
                    "type": "object",
                    "properties" : {
                        "x": { "type": "integer" },
                        "y": { "type": "integer" },
                        "z": { "type": "integer" }
                    }
                };
              var schema = {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates" },
                        "B": { "$ref": "https://example.com/coordinates" },
                        "C": { "$ref": "https://example.com/coordinates" }
                  };
              var valid = {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    };
              isCompliantWithExternalSchemas(valid, schema, externals) == true;
              var invalid = {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    };
              isCompliantWithExternalSchemas(invalid, schema, externals) == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isCompliantWithExternalSchemas(Val validationSubject, @JsonObject Val jsonSchema,
            Val externalSchemas) {
        if (validationSubject.isError()) {
            return validationSubject;
        }

        if (validationSubject.isUndefined()) {
            return Val.FALSE;
        }

        final var schemaMap = new HashMap<String, String>();
        if (externalSchemas.isArray()) {
            for (var externalSchema : externalSchemas.getArrayNode()) {
                if (externalSchema.has(ID)) {
                    schemaMap.put(externalSchema.get(ID).asText(), externalSchema.toString());
                }
            }
        }

        final var schemaLoader  = new MapSchemaLoader(schemaMap);
        final var schemaFactory = JsonSchemaFactory.builder()
                .schemaLoaders(schemaLoaders -> schemaLoaders.add(schemaLoader)).metaSchema(JsonMetaSchema.getV202012())
                .defaultMetaSchemaIri(JsonMetaSchema.getV202012().getIri()).build();

        try {
            final var validator = schemaFactory.getSchema(jsonSchema.getJsonNode());
            final var messages  = validator.validate(validationSubject.get());
            return Val.of(messages.isEmpty());
        } catch (JsonSchemaException e) {
            return Val.FALSE;
        }
    }

}
