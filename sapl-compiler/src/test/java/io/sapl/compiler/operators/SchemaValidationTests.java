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

import io.sapl.api.value.ErrorValue;
import io.sapl.api.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SchemaValidation.
 */
class SchemaValidationTests {

    // ============================================================
    // Basic Validation Tests
    // ============================================================

    @Test
    void validatorReturnsTrue() {
        var schema    = Value.ofObject(Map.of("type", Value.of("string")));
        var validator = SchemaValidation.schemaValidatorFromSchema(schema);
        var result    = validator.apply(Value.of("alice"));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void validatorReturnsFalse() {
        var schema    = Value.ofObject(Map.of("type", Value.of("string")));
        var validator = SchemaValidation.schemaValidatorFromSchema(schema);
        var result    = validator.apply(Value.of(42));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void validatorCanBeReused() {
        var schema    = Value.ofObject(Map.of("type", Value.of("number")));
        var validator = SchemaValidation.schemaValidatorFromSchema(schema);

        assertThat(validator.apply(Value.of(42))).isEqualTo(Value.TRUE);
        assertThat(validator.apply(Value.of("text"))).isEqualTo(Value.FALSE);
        assertThat(validator.apply(Value.of(3.14))).isEqualTo(Value.TRUE);
    }

    // ============================================================
    // Schema Compilation Error Tests
    // ============================================================

    @Test
    void unmarshallableSchemaReturnsErrorFunction() {
        var validator = SchemaValidation.schemaValidatorFromSchema(Value.UNDEFINED);
        var result    = validator.apply(Value.of("test"));

        assertThat(result instanceof ErrorValue).isTrue();
    }

    @Test
    void errorValueSchemaReturnsErrorFunction() {
        var errorSchema = Value.error("Bad schema");
        var validator   = SchemaValidation.schemaValidatorFromSchema(errorSchema);
        var result      = validator.apply(Value.of("test"));

        assertThat(result instanceof ErrorValue).isTrue();
    }

    // ============================================================
    // Validation Error Tests
    // ============================================================

    @Test
    void validatorReturnsErrorForUndefinedValue() {
        var schema    = Value.ofObject(Map.of("type", Value.of("string")));
        var validator = SchemaValidation.schemaValidatorFromSchema(schema);
        var result    = validator.apply(Value.UNDEFINED);

        assertThat(result instanceof ErrorValue).isTrue();
    }

    @Test
    void validatorReturnsErrorForErrorValue() {
        var schema     = Value.ofObject(Map.of("type", Value.of("string")));
        var validator  = SchemaValidation.schemaValidatorFromSchema(schema);
        var errorValue = Value.error("Something went wrong");
        var result     = validator.apply(errorValue);

        assertThat(result instanceof ErrorValue).isTrue();
    }

    // ============================================================
    // Access Control Example
    // ============================================================

    @Test
    void validatesAccessControlSubject() {
        var schema = Value.ofObject(Map.of("type", Value.of("object"), "properties",
                Value.ofObject(Map.of("userId", Value.ofObject(Map.of("type", Value.of("string"))), "clearanceLevel",
                        Value.ofObject(
                                Map.of("type", Value.of("number"), "minimum", Value.of(1), "maximum", Value.of(5))))),
                "required", Value.ofArray(Value.of("userId"), Value.of("clearanceLevel"))));

        var validator = SchemaValidation.schemaValidatorFromSchema(schema);

        var validSubject = Value.ofObject(Map.of("userId", Value.of("alice"), "clearanceLevel", Value.of(3)));

        var invalidSubject = Value.ofObject(Map.of("userId", Value.of("bob"), "clearanceLevel", Value.of(10)));

        assertThat(validator.apply(validSubject)).isEqualTo(Value.TRUE);
        assertThat(validator.apply(invalidSubject)).isEqualTo(Value.FALSE);
    }
}
