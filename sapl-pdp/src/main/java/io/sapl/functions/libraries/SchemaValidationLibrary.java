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

import com.networknt.schema.SchemaException;
import com.networknt.schema.Error;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import io.sapl.compiler.util.JsonSchemaPreparation;
import io.sapl.compiler.util.LinearRegex;
import io.sapl.compiler.util.SchemaErrors;
import lombok.val;

import java.util.List;

@FunctionLibrary(name = SchemaValidationLibrary.NAME, description = SchemaValidationLibrary.DESCRIPTION, libraryDocumentation = SchemaValidationLibrary.DOCUMENTATION)
public class SchemaValidationLibrary {

    public static final String NAME          = "jsonschema";
    public static final String DESCRIPTION   = "This library contains the functions for testing the compliance of a value with a JSON schema.";
    public static final String DOCUMENTATION = """
            # JSON Schema Validation

            Validate data structures against JSON Schema. The default dialect is 2020-12.
            Two modes: simple boolean checks and detailed validation results with errors.

            ## Function Selection

            | Need                    | Function                         | Returns  |
            |-------------------------|----------------------------------|----------|
            | Pass/fail check         | `isCompliant`                    | boolean  |
            | Pass/fail with $ref     | `isCompliantWithExternalSchemas` | boolean  |
            | Detailed errors         | `validate`                       | object   |
            | Detailed with $ref      | `validateWithExternalSchemas`    | object   |

            ## Simple Validation

            Check if input matches expected structure:

            ```sapl
            policy "validate request body"
            permit
                action == "api:create";
                var jsonSchema = {
                    "type": "object",
                    "properties": {
                        "name": { "type": "string", "minLength": 1 },
                        "age":  { "type": "integer", "minimum": 0 }
                    },
                    "required": ["name"]
                };
                jsonschema.isCompliant(resource.body, jsonSchema);
            ```

            Example: `resource.body = {"name": "Alice", "age": 30}`. Schema requires
            object with string "name" (min 1 char) and optional integer "age" (min 0).
            Returns true.

            ## Using $ref for Schema Composition

            When schemas reference other schemas via `$ref`, pass referenced schemas
            in the `externalSchemas` array. Each external schema must have an `$id`
            field matching the `$ref` URI.

            ```sapl
            policy "validate with shared schemas"
            permit
                var addressSchema = {
                    "$id": "https://example.com/address",
                    "type": "object",
                    "properties": {
                        "city": { "type": "string" },
                        "zip":  { "type": "string", "pattern": "^[0-9]{5}$" }
                    },
                    "required": ["city", "zip"]
                };
                var orderSchema = {
                    "type": "object",
                    "properties": {
                        "shipping": { "$ref": "https://example.com/address" }
                    }
                };
                jsonschema.isCompliantWithExternalSchemas(
                    resource.order, orderSchema, [addressSchema]
                );
            ```

            Example: `resource.order = {"shipping": {"city": "Berlin", "zip": "10115"}}`.
            The orderSchema references addressSchema via `$ref`. The validator resolves
            `$ref` by matching the URI to the `$id` in externalSchemas. Returns true.

            ## Detailed Error Information

            Use `validate` functions to get error locations and messages:

            ```sapl
            policy "reject with details"
            deny
                action == "submit";
                var result = jsonschema.validate(resource.form, resource.formSchema);
                !result.valid;
            obligation
                { "type": "validationError", "errors": result.errors }
            ```

            Example: If `resource.form = {"zip": "ABC"}` fails pattern validation,
            `result.errors` contains: `[{"path": "/zip", "message": "does not match
            pattern ^[0-9]{5}$", "type": "pattern", "schemaPath": "/properties/zip"}]`.

            ## Patterns

            The `pattern` and `patternProperties` keywords use the syntax of the Rust
            regex crate (https://docs.rs/regex/1.13.2/regex/#syntax), the same syntax as
            the `=~` operator, and are matched in time linear in the length of the text. A
            pattern matches if it matches any part of the text, and `$` matches at
            the end of the text only. Look-around, backreferences and atomic groups do
            not exist in this syntax. A schema with a pattern the regex engine refuses is
            an error, even if the subject never reaches the pattern. A
            rule written with look-ahead is written as several `pattern` keywords under
            `allOf` instead:
            `{"type": "string", "minLength": 12, "allOf": [{"pattern": "[0-9]"}, {"pattern": "[A-Z]"}]}`.

            A text longer than the text limit that a pattern would search makes the
            validation an error, and so does a property name longer than the text limit
            anywhere in the subject.

            The Java engine and the Rust engine differ for a schema of draft 6 or draft 7
            with one of these assertions in a key of `patternProperties`: an ASCII word
            boundary (`(?-u:\\b)`, `(?-u:\\B)`, `(?-u:\\b{start})`, `(?-u:\\b{end})`), a half
            word boundary (`\\b{start-half}`, `\\b{end-half}`) or a line anchor of the mode
            `(?mR)`. The Java engine reads such a key like any other pattern. The Rust
            engine refuses the schema: its schema validator checks the keys of these two
            drafts as ECMA-262 expressions, and ECMA-262 cannot state these assertions.

            Drafts 4, 6, 7, 2019-09 and 2020-12 are supported. The default is 2020-12.
            Custom meta-schemas named by `$schema` are outside the portable SAPL profile.
            Draft 2020-12 `$dynamicRef` can address a document supplied in the external schemas
            array by its `$id`, including a named `$dynamicAnchor`.
            A schema selects its dialect with `$schema`. A provided external schema without
            `$schema` inherits the root schema's dialect. An explicit declaration in the
            external resource takes precedence.

            References can resolve within the current schema, to bundled meta-schemas or to supplied
            resources. SAPL checks ordinary `$ref` targets at load time, including those in unused definitions.
            In draft 2019-09, `$recursiveRef` must be `"#"`. Any other value is a schema error. Definitions
            should use regular schema locations such as `$defs` or `definitions`. Reference
            targets in arbitrary annotation fields have no portable meaning. Neither engine
            fetches a resource for a schema. Host-relative references such as `//host/path`
            are refused, though the error's URI text can differ between engines.
            """;

    // JSON result field names
    private static final String FIELD_VALID  = "valid";
    private static final String FIELD_ERRORS = "errors";

    // JSON errors object field names
    private static final String FIELD_MESSAGE     = "message";
    private static final String FIELD_PATH        = "path";
    private static final String FIELD_SCHEMA_PATH = "schemaPath";
    private static final String FIELD_TYPE        = "type";

    // Error messages
    private static final String ERROR_FAILED_TO_CONVERT_VALUE_TO_JSON = "Failed to convert value to JSON: %s.";
    private static final String ERROR_SCHEMA_VALIDATION_FAILED        = "Schema validation failed: %s.";
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

    @Function(docs = """
            ```isCompliant(validationSubject, OBJECT schema)```:
            This function tests the `validationSubject` for compliance with the provided JSON schema `schema`.

            An invalid schema returns an error.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.

            The default dialect is [JSON Schema 2020-12](https://json-schema.org/draft/2020-12).

            **Example:**
            ```sapl
            policy "example"
            permit
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
            This function tests the ```validationSubject``` for compliance with the provided JSON
            schema `schema`.

            An invalid schema returns an error.
            If ```validationSubject``` is compliant with the ```schema```, the function returns ```true```,
            else it returns ```false```.
            If the ```jsonSchema``` contains external references to other ```schemas```, the validation function
            looks up the schemas in ```externalSchemas``` based on explicitly defined ```$id``` field in the schemas.
            If no $id field is provided, the schema will not be detectable.

            The default dialect is [JSON Schema 2020-12](https://json-schema.org/draft/2020-12).

            **Example:**
            ```sapl
            policy "example"
            permit
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
              var jsonSchema = {
                    "$id": "https://example.com/triangle.schema.json",
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "title": "Triangle",
                    "type": "object",
                    "properties": {
                        "A": { "$ref": "https://example.com/coordinates" },
                        "B": { "$ref": "https://example.com/coordinates" },
                        "C": { "$ref": "https://example.com/coordinates" }
                    }
                  };
              var valid = {
                       "A" : { "x" : 1, "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    };
              jsonschema.isCompliantWithExternalSchemas(valid, jsonSchema, [externals]) == true;
              var invalid = {
                       "A" : { "x" : "I AM NOT A NUMBER I AM A FREE MAN", "y" : 2, "z" : 3 },
                       "B" : { "x" : 1, "y" : 2, "z" : 3 },
                       "C" : { "x" : 1, "y" : 2, "z" : 3 }
                    };
              jsonschema.isCompliantWithExternalSchemas(invalid, jsonSchema, [externals]) == false;
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

            An invalid schema returns an error.

            The default dialect is [JSON Schema 2020-12](https://json-schema.org/draft/2020-12).

            **Example:**
            ```sapl
            policy "validate_document_metadata"
            permit action == "upload:document";
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

            An invalid schema returns an error.
            If the ```jsonSchema``` contains external references to other ```schemas```, the validation function
            looks up the schemas in ```externalSchemas``` based on explicitly defined ```$id``` field in the schemas.
            If no $id field is provided, the schema will not be detectable.

            The default dialect is [JSON Schema 2020-12](https://json-schema.org/draft/2020-12).

            **Example:**
            ```sapl
            policy "validate_api_request_with_shared_schemas"
            permit action == "api:call";
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

        try {
            val registry  = JsonSchemaPreparation.registry(externalSchemas);
            val validator = JsonSchemaPreparation.prepareValidation(registry, jsonSchema);
            if (validationSubject instanceof UndefinedValue) {
                return createValidationResult(false, List.of());
            }
            val subjectNode = ValueJsonMarshaller.toJsonNode(validationSubject);
            JsonSchemaPreparation.requireSearchablePropertyNames(subjectNode, LinearRegex.LIMITS.maxTextBytes());
            val errors = validator.validate(subjectNode);
            return createValidationResult(errors.isEmpty(), errors);
        } catch (SchemaException e) {
            // An uncompilable schema (malformed, bad or unresolvable $ref) is an author
            // error, not a non-compliant subject. Surface it as an error.
            return Value.error(SchemaErrors.describe(e, ERROR_SCHEMA_VALIDATION_FAILED));
        } catch (IllegalArgumentException e) {
            return Value.error(ERROR_FAILED_TO_CONVERT_VALUE_TO_JSON, e);
        } catch (Throwable t) {
            // Third-party validation on hostile input must not crash evaluation.
            return Value.error(SchemaErrors.describe(t, ERROR_SCHEMA_VALIDATION_FAILED));
        }
    }

    private static Value createValidationResult(boolean valid, List<Error> errors) {
        val resultBuilder = ObjectValue.builder();
        resultBuilder.put(FIELD_VALID, Value.of(valid));

        val errorsBuilder = ArrayValue.builder();
        for (val error : errors) {
            val errorBuilder = ObjectValue.builder();
            errorBuilder.put(FIELD_PATH, Value.of(error.getInstanceLocation().toString()));
            errorBuilder.put(FIELD_MESSAGE, Value.of(error.getMessage()));
            errorBuilder.put(FIELD_TYPE, Value.of(error.getKeyword()));
            errorBuilder.put(FIELD_SCHEMA_PATH, Value.of(error.getEvaluationPath().toString()));
            errorsBuilder.add(errorBuilder.build());
        }
        resultBuilder.put(FIELD_ERRORS, errorsBuilder.build());

        return resultBuilder.build();
    }

}
