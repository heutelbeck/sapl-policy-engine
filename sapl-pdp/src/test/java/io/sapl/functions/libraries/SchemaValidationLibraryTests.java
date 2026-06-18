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

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.sapl.functions.libraries.SchemaValidationLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SchemaValidationLibrary")
class SchemaValidationLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new SchemaValidationLibrary())).doesNotThrowAnyException();
    }

    @Test
    @Timeout(15)
    @DisplayName("a catastrophically backtracking schema pattern aborts under the match budget instead of hanging the evaluation thread")
    void whenSchemaPatternIsCatastrophicallyBacktrackingThenBoundedAbort() {
        val schema  = ObjectValue.builder().put("type", Value.of("string")).put("pattern", Value.of("(.*a){30}$"))
                .build();
        val hostile = Value.of("a".repeat(50));

        val result = isCompliant(hostile, schema);

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                e -> assertThat(e.message()).contains("time budget"));
    }

    @Test
    @DisplayName("a validator failure outside the expected exception types yields an ErrorValue, not an escape")
    void whenValidatorThrowsUnexpectedlyThenErrorValue() {
        // A self-referential $ref drives networknt into a StackOverflowError, which is
        // neither SchemaException nor IllegalArgumentException.
        val selfReferential = ObjectValue.builder().put("$ref", Value.of("#")).build();

        val result = validateWithExternalSchemas(Value.of("x"), selfReferential, Value.EMPTY_ARRAY);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    @DisplayName("an unresolvable $ref makes the schema un-compilable and yields an ErrorValue, not a false validation result")
    void whenSchemaHasUnresolvableRefThenErrorValue() {
        val brokenSchema = ObjectValue.builder().put("$ref", Value.of("#/$defs/missing")).build();

        val result = validateWithExternalSchemas(Value.of("x"), brokenSchema, Value.EMPTY_ARRAY);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenValidatingCompliantValueThenReturnsTrue() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.TRUE, schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void whenValidatingNonCompliantValueThenReturnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.of(123), schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
    }

    @Test
    void whenValidatingUndefinedValueThenReturnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("string")).build();

        val result = validate(Value.UNDEFINED, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.FALSE);
        assertThat((ArrayValue) resultObj.get("errors")).isEmpty();
    }

    @Test
    void whenValidatingErrorValueThenPropagatesError() {
        val schema     = ObjectValue.builder().put("type", Value.of("string")).build();
        val errorValue = Value.error("test error");

        val result = validate(errorValue, schema);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenValidateReturnsCompliantThenStructuredResultCorrect() {
        val schema = ObjectValue.builder().put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("name", ObjectValue.builder().put("type", Value.of("string")).build())
                                .put("age", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val subject = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of(25)).build();

        val result = validate(subject, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj).containsKey("valid").containsKey("errors");
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.TRUE);
        assertThat((ArrayValue) resultObj.get("errors")).isEmpty();
    }

    @Test
    void whenValidateReturnsNonCompliantThenStructuredResultWithErrors() {
        val schema = ObjectValue.builder().put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("name", ObjectValue.builder().put("type", Value.of("string")).build())
                                .put("age", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val subject = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of("25")) // Should be
                                                                                                      // integer, not
                                                                                                      // string
                .build();

        val result = validate(subject, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.FALSE);
        val errors = (ArrayValue) resultObj.get("errors");
        assertThat(errors).isNotEmpty();

        val firstError = (ObjectValue) errors.getFirst();
        assertThat(firstError.containsKey("path")).isTrue();
        assertThat(firstError.containsKey("message")).isTrue();
        assertThat(firstError.containsKey("type")).isTrue();
        assertThat(firstError.containsKey("schemaPath")).isTrue();
    }

    @Test
    void whenValidateWithExternalSchemasThenReferencesAreResolved() {
        val coordinatesSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/coordinates"))
                .put("$schema", Value.of("https://json-schema.org/draft/2020-12/schema"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("x", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("y", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("z", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val externals = ArrayValue.builder().add(coordinatesSchema).build();

        val triangleSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/triangle"))
                .put("type", Value.of("object"))
                .put("properties", ObjectValue.builder()
                        .put("A",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .put("B",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .put("C",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .build())
                .build();

        val validTriangle = ObjectValue.builder()
                .put("A",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .put("B",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .put("C",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .build();

        val result = isCompliantWithExternalSchemas(validTriangle, triangleSchema, externals);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void whenValidateWithExternalSchemasAndInvalidDataThenReturnsFalse() {
        val coordinatesSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/coordinates"))
                .put("$schema", Value.of("https://json-schema.org/draft/2020-12/schema"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("x", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("y", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("z", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val externals = ArrayValue.builder().add(coordinatesSchema).build();

        val triangleSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/triangle"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("A",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                                .build())
                .build();

        val invalidTriangle = ObjectValue.builder().put("A", ObjectValue.builder().put("x", Value.of("not a number"))
                .put("y", Value.of(2)).put("z", Value.of(3)).build()).build();

        val result = isCompliantWithExternalSchemas(invalidTriangle, triangleSchema, externals);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
    }
}
