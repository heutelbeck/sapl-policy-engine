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
import org.junit.jupiter.api.Test;

import static io.sapl.functions.libraries.SchemaValidationLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SchemaValidationLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(SchemaValidationLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void when_validatingCompliantValue_then_returnsTrue() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.TRUE, schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void when_validatingNonCompliantValue_then_returnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.of(123), schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
    }

    @Test
    void when_validatingUndefinedValue_then_returnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("string")).build();

        val result = validate(Value.UNDEFINED, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.FALSE);
        assertThat((ArrayValue) resultObj.get("errors")).isEmpty();
    }

    @Test
    void when_validatingErrorValue_then_propagatesError() {
        val schema     = ObjectValue.builder().put("type", Value.of("string")).build();
        val errorValue = Value.error("test error");

        val result = validate(errorValue, schema);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_validateReturnsCompliant_then_structuredResultCorrect() {
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
    void when_validateReturnsNonCompliant_then_structuredResultWithErrors() {
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
    void when_validateWithExternalSchemas_then_referencesAreResolved() {
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
    void when_validateWithExternalSchemasAndInvalidData_then_returnsFalse() {
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
