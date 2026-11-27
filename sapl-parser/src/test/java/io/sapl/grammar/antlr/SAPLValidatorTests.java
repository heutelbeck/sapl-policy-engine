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
package io.sapl.grammar.antlr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.grammar.antlr.validation.SAPLValidator;
import io.sapl.grammar.antlr.validation.ValidationError;

class SAPLValidatorTests {

    static Stream<Arguments> attributesInSchema() {
        return Stream.of(arguments("environment attribute in schema", """
                subject schema <time.now>
                policy "test" permit
                """), arguments("head environment attribute in schema", """
                action schema |<clock.ticker>
                policy "test" permit
                """), arguments("attribute step in schema expression", """
                resource schema subject.<pip.attribute>
                policy "test" permit
                """), arguments("attribute in variable schema", """
                policy "test" permit
                    var x = 123 schema subject.<pip.schema>;
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attributesInSchema")
    void whenAttributeInSchema_thenValidationError(String description, String policy) {
        var errors = validatePolicy(policy);

        assertThat(errors).as("Should detect attribute in schema: %s", description).isNotEmpty()
                .anyMatch(e -> e.message().contains("Attribute access is forbidden in schema"));
    }

    // Valid Policy Tests (no validation errors expected)
    // Note: Reserved words as variable names (subject, action, resource,
    // environment) are caught at SYNTAX level (see
    // SAPLParserTests.whenUsingReservedWordAsVariableName)
    // because the grammar uses ID token which doesn't match reserved word tokens.

    static Stream<Arguments> validPolicies() {
        return Stream.of(arguments("simple permit policy", """
                policy "test" permit
                """), arguments("policy with operators in body", """
                policy "test"
                permit
                    subject == "admin" && action == "read";
                    subject == "user" || subject == "guest";
                """), arguments("attributes in policy body", """
                policy "test"
                permit
                    <time.now> != undefined;
                    subject.<pip.attribute> == "value";
                    |<clock.ticker> != undefined;
                    subject.|<pip.stream> == "value";
                """), arguments("policy set with valid structure", """
                set "test" priority deny or abstain errors propagate
                var globalVar = "value";
                policy "p1" permit subject == "admin";
                policy "p2" deny resource == "secret";
                """), arguments("static schema expressions", """
                subject schema { "type": "object" }
                action enforced schema { "type": "string" }
                resource schema { "required": ["id"] }
                environment schema { "type": "object" }

                policy "test" permit
                    var x = 123 schema { "type": "number" };
                """), arguments("normal variable names", """
                policy "test" permit
                    var mySubject = subject;
                    var actionName = action;
                    var resourceId = resource.id;
                    var envValue = environment.value;
                """), arguments("reserved words as field access", """
                policy "test" permit
                    subject.subject == "value";
                    action.action == "read";
                    resource.resource == "data";
                    environment.environment == "prod";
                """), arguments("complex policy with all features", """
                import filter.blacken

                subject enforced schema { "type": "object", "required": ["id"] }

                policy "complex"
                permit
                    var userId = subject.id;
                    userId != null;
                    subject.role == "admin";
                    action == "read";
                    <time.now> != undefined;
                    subject.<acl.check(resource.id)> == true;
                obligation
                    { "type": "log", "userId": subject.id }
                advice
                    { "type": "cache", "duration": 300 }
                transform
                    resource |- { @.secret : blacken }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPolicies")
    void whenValidPolicy_thenNoValidationErrors(String description, String policy) {
        var errors = validatePolicy(policy);

        assertThat(errors).as("Valid policy '%s' should have no validation errors", description).isEmpty();
    }

    // Validation Error Details Tests

    @Test
    void whenValidationError_thenErrorContainsLineAndColumn() {
        var policy = """
                subject schema <time.now>
                policy "test" permit
                """;

        var errors = validatePolicy(policy);

        assertThat(errors).isNotEmpty().first().satisfies(error -> {
            assertThat(error.line()).isGreaterThan(0);
            assertThat(error.charPositionInLine()).isGreaterThanOrEqualTo(0);
            assertThat(error.offendingText()).isNotEmpty();
        });
    }

    @Test
    void whenValidationError_thenToStringFormatsCorrectly() {
        var policy = """
                subject schema <time.now>
                policy "test" permit
                """;

        var errors = validatePolicy(policy);

        assertThat(errors).isNotEmpty().first().extracting(ValidationError::toString).asString()
                .matches("line \\d+:\\d+ .*");
    }

    private List<ValidationError> validatePolicy(String input) {
        var charStream  = CharStreams.fromString(input);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);

        // Remove default error listeners to avoid console output
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        var tree      = parser.sapl();
        var validator = new SAPLValidator();

        return validator.validate(tree);
    }

}
