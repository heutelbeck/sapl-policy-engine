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
package io.sapl.test.grammar.antlr.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser;

@DisplayName("Validation error tests")
class ValidationErrorTests {

    @Test
    @DisplayName("all fields are accessible when creating validation error")
    void whenCreatingValidationError_thenAllFieldsAreAccessible() {
        var error = new ValidationError("The stars are not aligned.", 13, 42, "Cthulhu");

        assertThat(error.message()).isEqualTo("The stars are not aligned.");
        assertThat(error.line()).isEqualTo(13);
        assertThat(error.charPositionInLine()).isEqualTo(42);
        assertThat(error.offendingText()).isEqualTo("Cthulhu");
    }

    @Test
    @DisplayName("toString formats correctly")
    void whenCallingToString_thenFormatsCorrectly() {
        var error = new ValidationError("Forbidden knowledge detected.", 7, 23, "necronomicon");

        assertThat(error).hasToString("line 7:23 Forbidden knowledge detected.");
    }

    @Test
    @DisplayName("creating from token extracts token information")
    void whenCreatingFromToken_thenExtractsTokenInformation() {
        var document = """
                requirement "Arkham" {
                    scenario "test"
                        when "cultist" attempts "summon" on "shoggoth"
                        expect permit;
                }
                """;
        var lexer    = new SAPLTestLexer(CharStreams.fromString(document));
        var tokens   = new CommonTokenStream(lexer);
        var parser   = new SAPLTestParser(tokens);
        var context  = parser.saplTest();

        var requirementNameToken = context.requirement(0).name;

        var error = ValidationError.fromToken("Duplicate requirement name.", requirementNameToken);

        assertThat(error.message()).isEqualTo("Duplicate requirement name.");
        assertThat(error.line()).isEqualTo(1);
        assertThat(error.charPositionInLine()).isEqualTo(12);
        assertThat(error.offendingText()).isEqualTo("\"Arkham\"");
    }

    @Test
    @DisplayName("creating from context extracts context information")
    void whenCreatingFromContext_thenExtractsContextInformation() {
        var document = """
                requirement "Miskatonic" {
                    scenario "library access"
                        when "student" attempts "read" on "tome"
                        expect permit;
                }
                """;
        var lexer    = new SAPLTestLexer(CharStreams.fromString(document));
        var tokens   = new CommonTokenStream(lexer);
        var parser   = new SAPLTestParser(tokens);
        var context  = parser.saplTest();

        var scenarioContext = context.requirement(0).scenario(0);

        var error = ValidationError.fromContext("Scenario validation failed.", scenarioContext);

        assertThat(error.message()).isEqualTo("Scenario validation failed.");
        assertThat(error.line()).isEqualTo(2);
        assertThat(error.charPositionInLine()).isEqualTo(4);
        assertThat(error.offendingText()).isNotEmpty();
    }

    @Test
    @DisplayName("creating from context with null start token returns default values")
    void whenCreatingFromContextWithNullStartToken_thenReturnsDefaultValues() {
        var error = ValidationError.fromContext("Error with null context.", null);

        assertThat(error.message()).isEqualTo("Error with null context.");
        assertThat(error.line()).isZero();
        assertThat(error.charPositionInLine()).isZero();
        assertThat(error.offendingText()).isEmpty();
    }

    @Test
    @DisplayName("two errors with same values are equal")
    void whenTwoErrorsHaveSameValues_thenTheyAreEqual() {
        var error1 = new ValidationError("Eldritch error.", 5, 10, "dagon");
        var error2 = new ValidationError("Eldritch error.", 5, 10, "dagon");

        assertThat(error1).isEqualTo(error2).hasSameHashCodeAs(error2);
    }

    @Test
    @DisplayName("two errors with different values are not equal")
    void whenTwoErrorsHaveDifferentValues_thenTheyAreNotEqual() {
        var error1 = new ValidationError("Eldritch error.", 5, 10, "dagon");
        var error2 = new ValidationError("Cosmic error.", 5, 10, "dagon");
        var error3 = new ValidationError("Eldritch error.", 6, 10, "dagon");
        var error4 = new ValidationError("Eldritch error.", 5, 11, "dagon");
        var error5 = new ValidationError("Eldritch error.", 5, 10, "cthulhu");

        assertThat(error1).isNotEqualTo(error2).isNotEqualTo(error3).isNotEqualTo(error4).isNotEqualTo(error5);
    }

}
