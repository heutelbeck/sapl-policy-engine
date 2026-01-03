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
package io.sapl.functions.libraries;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.MapSchemaLoader;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.Set;

@UtilityClass
@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION)
public class SchemaValidationLibrary {

    public static final String NAME        = "jsonschema";
    public static final String DESCRIPTION = "This library contains the functions for testing the compliance of a value with a JSON schema.";

    // JSON Schema field names
    private static final String SCHEMA_ID_FIELD = "$id";

    // JSON result field names
    private static final String FIELD_VALID  = "valid";
    private static final String FIELD_ERRORS = "errors";

    // JSON error object field names
    private static final String FIELD_MESSAGE     = "message";
    private static final String FIELD_PATH        = "path";
    private static final String FIELD_SCHEMA_PATH = "schemaPath";
    private static final String FIELD_TYPE        = "type";

    // Error messages
    private static final String ERROR_FAILED_TO_CONVERT_VALUE_TO_JSON = "Failed to convert value to JSON: %s.";
    private static final String ERROR_UNEXPECTED_VALIDATION_RESULT    = "Unexpected validation result data.";

    // Return type schemas for IDE support
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
    public static Value isCompliant(Value validationSubject, ObjectValue jsonSchema) {
        val result = validate(validationSubject, jsonSchema);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof ObjectValue obj) {
            val validField = obj.get(FIELD_VALID);
            if (validField instanceof BooleanValue booleanValue) {
                return booleanValue;
            }
        }
        return Value.error(ERROR_UNEXPECTED_VALIDATION_RESULT);
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
    public static Value isCompliantWithExternalSchemas(Value validationSubject, ObjectValue jsonSchema,
            Value externalSchemas) {
        val result = validateWithExternalSchemas(validationSubject, jsonSchema, externalSchemas);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof ObjectValue obj) {
            val validField = obj.get(FIELD_VALID);
            if (validField instanceof BooleanValue booleanValue) {
                return booleanValue;
            }
        }
        return Value.error(ERROR_UNEXPECTED_VALIDATION_RESULT);
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
    public static Value validate(Value validationSubject, ObjectValue jsonSchema) {
        return validateWithExternalSchemas(validationSubject, jsonSchema, Value.EMPTY_ARRAY);
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
    public static Value validateWithExternalSchemas(Value validationSubject, ObjectValue jsonSchema,
            Value externalSchemas) {
        if (validationSubject instanceof ErrorValue) {
            return validationSubject;
        }

        if (validationSubject instanceof UndefinedValue) {
            return createValidationResult(false, Set.of());
        }
        try {
            val schemaMap   = buildSchemaMap(externalSchemas);
            val factory     = createSchemaFactory(schemaMap);
            val schemaNode  = ValueJsonMarshaller.toJsonNode(jsonSchema);
            val validator   = factory.getSchema(schemaNode);
            val subjectNode = ValueJsonMarshaller.toJsonNode(validationSubject);
            val messages    = validator.validate(subjectNode);
            return createValidationResult(messages.isEmpty(), messages);
        } catch (JsonSchemaException e) {
            return createValidationResult(false, Set.of());
        } catch (IllegalArgumentException e) {
            return Value.error(ERROR_FAILED_TO_CONVERT_VALUE_TO_JSON, e);
        }
    }

    private static HashMap<String, String> buildSchemaMap(Value externalSchemas) {
        val schemaMap = new HashMap<String, String>();
        if (externalSchemas instanceof ArrayValue array) {
            for (val externalSchema : array) {
                if (externalSchema instanceof ObjectValue obj && obj.containsKey(SCHEMA_ID_FIELD)) {
                    val id = obj.get(SCHEMA_ID_FIELD);
                    if (id instanceof TextValue text) {
                        val schemaNode = ValueJsonMarshaller.toJsonNode(obj);
                        schemaMap.put(text.value(), schemaNode.toString());
                    }
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

    private static Value createValidationResult(boolean valid, Set<ValidationMessage> messages) {
        val resultBuilder = ObjectValue.builder();
        resultBuilder.put(FIELD_VALID, Value.of(valid));

        val errorsBuilder = ArrayValue.builder();
        for (var message : messages) {
            val errorBuilder = ObjectValue.builder();
            errorBuilder.put(FIELD_PATH, Value.of(message.getInstanceLocation().toString()));
            errorBuilder.put(FIELD_MESSAGE, Value.of(message.getMessage()));
            errorBuilder.put(FIELD_TYPE, Value.of(message.getType()));
            errorBuilder.put(FIELD_SCHEMA_PATH, Value.of(message.getEvaluationPath().toString()));
            errorsBuilder.add(errorBuilder.build());
        }
        resultBuilder.put(FIELD_ERRORS, errorsBuilder.build());

        return resultBuilder.build();
    }

}
