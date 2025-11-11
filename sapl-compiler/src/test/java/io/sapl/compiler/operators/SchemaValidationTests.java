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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidationTests {

    private static final Value STRING_SCHEMA = Value.ofObject(Map.of("type", Value.of("string")));
    private static final Value NUMBER_SCHEMA = Value.ofObject(Map.of("type", Value.of("number")));

    @ParameterizedTest
    @MethodSource("validationTestCases")
    void validatesValueAgainstSchema(Value schema, Value input, Value expected) {
        val validator = SchemaValidation.schemaValidatorFromSchema(schema);
        assertThat(validator.apply(input)).isEqualTo(expected);
    }

    static Stream<Arguments> validationTestCases() {
        return Stream.of(Arguments.of(STRING_SCHEMA, Value.of("Frodo"), Value.TRUE),
                Arguments.of(STRING_SCHEMA, Value.of(9), Value.FALSE),
                Arguments.of(NUMBER_SCHEMA, Value.of(20), Value.TRUE),
                Arguments.of(NUMBER_SCHEMA, Value.of("Gandalf"), Value.FALSE));
    }

    @Test
    void validatorCanBeReusedForMultipleRingBearers() {
        val validator = SchemaValidation.schemaValidatorFromSchema(NUMBER_SCHEMA);

        assertThat(validator.apply(Value.of(3))).isEqualTo(Value.TRUE);
        assertThat(validator.apply(Value.of("Sauron"))).isEqualTo(Value.FALSE);
        assertThat(validator.apply(Value.of(9))).isEqualTo(Value.TRUE);
    }

    @ParameterizedTest
    @MethodSource("invalidSchemaTestCases")
    void returnsErrorForInvalidSchema(Value invalidSchema) {
        val validator = SchemaValidation.schemaValidatorFromSchema(invalidSchema);
        val result    = validator.apply(Value.of("Aragorn"));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> invalidSchemaTestCases() {
        return Stream.of(Arguments.of(Value.UNDEFINED), Arguments.of(Value.error("Melkor corrupted the schema")));
    }

    @ParameterizedTest
    @MethodSource("invalidValueTestCases")
    void returnsErrorForInvalidValue(Value invalidValue) {
        val validator = SchemaValidation.schemaValidatorFromSchema(STRING_SCHEMA);
        val result    = validator.apply(invalidValue);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> invalidValueTestCases() {
        return Stream.of(Arguments.of(Value.UNDEFINED), Arguments.of(Value.error("The Ring has been destroyed")));
    }

    @Test
    void validatesMiddleEarthSecurityClearances() {
        val schema = Value.ofObject(Map.of("type", Value.of("object"), "properties",
                Value.ofObject(Map.of("bearer", Value.ofObject(Map.of("type", Value.of("string"))), "ringPower",
                        Value.ofObject(
                                Map.of("type", Value.of("number"), "minimum", Value.of(1), "maximum", Value.of(20))))),
                "required", Value.ofArray(Value.of("bearer"), Value.of("ringPower"))));

        val validator = SchemaValidation.schemaValidatorFromSchema(schema);

        val validRingBearer = Value.ofObject(Map.of("bearer", Value.of("Galadriel"), "ringPower", Value.of(15)));

        val invalidRingBearer = Value.ofObject(Map.of("bearer", Value.of("Sauron"), "ringPower", Value.of(100)));

        assertThat(validator.apply(validRingBearer)).isEqualTo(Value.TRUE);
        assertThat(validator.apply(invalidRingBearer)).isEqualTo(Value.FALSE);
    }
}
