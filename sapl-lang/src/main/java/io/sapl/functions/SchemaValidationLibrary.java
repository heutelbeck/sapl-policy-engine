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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.MapSchemaLoader;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.Set;

@UtilityClass
@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION)
public class SchemaValidationLibrary {

    public static final String NAME        = "jsonschema";
    public static final String DESCRIPTION = "This library contains the functions for testing the compliance of a value with a JSON schema.";

    private static final String SCHEMA_ID_FIELD = "$id";

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String VALIDATION_RESULT_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "valid": {
                        "type": "boolean",
                        "description": "Whether the subject is compliant with the schema"
                    },
                    "errors": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "path": {
                                    "type": "string",
                                    "description": "JSON pointer to the location of the validation error"
                                },
                                "message": {
                                    "type": "string",
                                    "description": "Human-readable error message"
                                },
                                "type": {
                                    "type": "string",
                                    "description": "The validation keyword that failed"
                                },
                                "schemaPath": {
                                    "type": "string",
                                    "description": "JSON pointer to the schema location"
                                }
                            },
                            "required": ["path", "message", "type", "schemaPath"]
                        },
                        "description": "Array of validation errors, empty if valid"
                    }
                },
                "required": ["valid", "errors"]
            }
            """;

    private static final JsonSchemaFactory SCHEMA_FACTORY = configureSchemaFactoryBuilder().build();

    private static JsonSchemaFactory.Builder configureSchemaFactoryBuilder() {
        return JsonSchemaFactory.builder().metaSchema(JsonMetaSchema.getV202012())
                .defaultMetaSchemaIri(JsonMetaSchema.getV202012().getIri());
    }

    @Function(docs = """
            ```isCompliant(validationSubject, OBJECT schema)```:
            This function tests the `validationSubject` for compliance with the with the provided JSON schema `schema`.

            The schema itself cannot be validated and improper schema definitions may lead to unexpected results.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```sapl
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
        val result = validate(validationSubject, jsonSchema);
        if (result.isError()) {
            return result;
        }
        return Val.of(result.get().get("valid").asBoolean());
    }

    @Function(docs = """
            ```isCompliantWithExternalSchemas(validationSubject, OBJECT jsonSchema, ARRAY externalSchemas)```:
            This function tests the ```validationSubject``` for compliance with the with the provided JSON
            schema `schema`.

            The schema itself cannot be validated and improper schema definitions may lead to unexcpected results.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.
            If the ```jsonSchema``` contains external references to other ```schemas```, the validation function
            looks up the schemas in ```externalSchemas``` based on explicitly defined ```$id``` field in the schemas.
            If no $id field is provided, the schema will not be detectable.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```sapl
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
        val result = validateWithExternalSchemas(validationSubject, jsonSchema, externalSchemas);
        if (result.isError()) {
            return result;
        }
        return Val.of(result.get().get("valid").asBoolean());
    }

    @Function(docs = """
            ```validate(validationSubject, OBJECT schema)```:
            This function validates the `validationSubject` against the provided JSON schema `schema` and returns
            a detailed validation result.

            The result contains a `valid` boolean field and an `errors` array with detailed information about any
            validation failures. Each error includes the location in the subject (`path`), a human-readable message,
            the validation keyword that failed (`type`), and the schema location (`schemaPath`).

            The schema itself cannot be validated and improper schema definitions may lead to unexpected results.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```sapl
            policy "validate_document_metadata"
            permit action == "upload:document"
            where
              var metadataSchema = {
                "type": "object",
                "properties": {
                  "classification": { "enum": ["public", "internal", "confidential", "secret"] },
                  "owner": { "type": "string", "minLength": 1 },
                  "createdAt": { "type": "string", "format": "date-time" }
                },
                "required": ["classification", "owner"]
              };
              var result = jsonschema.validate(resource.metadata, metadataSchema);
              result.valid;
            ```
            """, schema = VALIDATION_RESULT_SCHEMA)
    public static Val validate(Val validationSubject, @JsonObject Val jsonSchema) {
        return validateWithExternalSchemas(validationSubject, jsonSchema, Val.ofEmptyArray());
    }

    @Function(docs = """
            ```validateWithExternalSchemas(validationSubject, OBJECT jsonSchema, ARRAY externalSchemas)```:
            This function validates the ```validationSubject``` against the provided JSON schema `schema` and returns
            a detailed validation result including error details.

            The result contains a `valid` boolean field and an `errors` array with detailed information about any
            validation failures. Each error includes the location in the subject (`path`), a human-readable message,
            the validation keyword that failed (`type`), and the schema location (`schemaPath`).

            The schema itself cannot be validated and improper schema definitions may lead to unexpected results.
            If the ```jsonSchema``` contains external references to other ```schemas```, the validation function
            looks up the schemas in ```externalSchemas``` based on explicitly defined ```$id``` field in the schemas.
            If no $id field is provided, the schema will not be detectable.

            *Note:* The schema is expected to comply with: [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)

            **Example:**
            ```sapl
            policy "validate_api_request_with_shared_schemas"
            permit action == "api:call"
            where
              var addressSchema = {
                "$id": "https://schemas.company.com/address",
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                  "street": { "type": "string" },
                  "city": { "type": "string" },
                  "country": { "type": "string", "minLength": 2, "maxLength": 2 }
                },
                "required": ["street", "city", "country"]
              };
              var userSchema = {
                "$id": "https://schemas.company.com/user",
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "properties": {
                  "userId": { "type": "string", "format": "uuid" },
                  "email": { "type": "string", "format": "email" },
                  "role": { "enum": ["user", "admin", "auditor"] },
                  "address": { "$ref": "https://schemas.company.com/address" }
                },
                "required": ["userId", "email", "role"]
              };
              var result = jsonschema.validateWithExternalSchemas(
                resource.userData,
                userSchema,
                [addressSchema]
              );
              result.valid || result.errors[0].type == "required";
            ```
            """, schema = VALIDATION_RESULT_SCHEMA)
    public static Val validateWithExternalSchemas(Val validationSubject, @JsonObject Val jsonSchema,
            Val externalSchemas) {
        if (validationSubject.isError()) {
            return validationSubject;
        }

        if (validationSubject.isUndefined()) {
            return createValidationResult(false, Set.of());
        }

        val schemaMap = buildSchemaMap(externalSchemas);
        val factory   = createSchemaFactory(schemaMap);

        try {
            val validator = factory.getSchema(jsonSchema.getJsonNode());
            val messages  = validator.validate(validationSubject.get());
            return createValidationResult(messages.isEmpty(), messages);
        } catch (JsonSchemaException e) {
            return createValidationResult(false, Set.of());
        }
    }

    private static HashMap<String, String> buildSchemaMap(Val externalSchemas) {
        val schemaMap = new HashMap<String, String>();
        if (externalSchemas.isArray()) {
            for (var externalSchema : externalSchemas.getArrayNode()) {
                if (externalSchema.has(SCHEMA_ID_FIELD)) {
                    schemaMap.put(externalSchema.get(SCHEMA_ID_FIELD).asText(), externalSchema.toString());
                }
            }
        }
        return schemaMap;
    }

    private static JsonSchemaFactory createSchemaFactory(HashMap<String, String> schemaMap) {
        if (schemaMap.isEmpty()) {
            return SCHEMA_FACTORY;
        }
        val schemaLoader = new MapSchemaLoader(schemaMap);
        return configureSchemaFactoryBuilder().schemaLoaders(schemaLoaders -> schemaLoaders.add(schemaLoader)).build();
    }

    private static Val createValidationResult(boolean valid, Set<ValidationMessage> messages) {
        val factory = JsonNodeFactory.instance;
        val result  = factory.objectNode();
        result.put("valid", valid);

        val errorsArray = factory.arrayNode();
        for (var message : messages) {
            val errorObject = factory.objectNode();
            errorObject.put("path", message.getInstanceLocation().toString());
            errorObject.put("message", message.getMessage());
            errorObject.put("type", message.getType());
            errorObject.put("schemaPath", message.getEvaluationPath().toString());
            errorsArray.add(errorObject);
        }
        result.set("errors", errorsArray);

        return Val.of(result);
    }

}
