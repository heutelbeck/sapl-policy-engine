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
package io.sapl.compiler.expressions;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.util.Stratum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RegexCompilerTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_literalRegexExpression_then_returnsExpected(String description, String expression, Value expected) {
        assertCompilesTo(expression, expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_literalRegexExpression_then_returnsExpected() {
        return Stream.of(
            arguments("match", "\"hello\" =~ \"hello\"", Value.TRUE),
            arguments("no match", "\"hello\" =~ \"world\"", Value.FALSE),
            arguments("pattern match", "\"abc123\" =~ \"[a-z]+[0-9]+\"", Value.TRUE),
            arguments("number input", "5 =~ \".*\"", Value.FALSE),
            arguments("boolean input", "true =~ \".*\"", Value.FALSE),
            arguments("null input", "null =~ \".*\"", Value.FALSE),
            arguments("array input", "[] =~ \".*\"", Value.FALSE),
            arguments("object input", "{} =~ \".*\"", Value.FALSE));
    }
    // @formatter:on

    @Test
    void when_invalidRegexPattern_then_throwsException() {
        assertThatThrownBy(() -> compileExpression("\"hello\" =~ \"[invalid\""))
                .isInstanceOf(SaplCompilerException.class).hasMessageContaining("Invalid regular expression");
    }

    @Test
    void when_nonTextPattern_then_returnsError() {
        assertCompilesToError("\"hello\" =~ 123", "Regular expression must be a string");
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_regexExpression_then_hasExpectedStratum(String description, String expression, Stratum expected) {
        assertStratumOfCompiledExpression(expression, expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_regexExpression_then_hasExpectedStratum() {
        return Stream.of(
            arguments("literal =~ literal", "\"hello\" =~ \"hello\"", Stratum.VALUE),
            arguments("variable =~ literal", "subject.name =~ \"hello.*\"", Stratum.PURE_SUB),
            arguments("literal =~ variable", "\"hello\" =~ subject.pattern", Stratum.PURE_SUB),
            arguments("variable =~ variable", "subject.name =~ subject.pattern", Stratum.PURE_SUB),
            arguments("stream =~ literal", "subject.<test.name> =~ \"hello.*\"", Stratum.STREAM),
            arguments("literal =~ stream", "\"hello\" =~ subject.<test.pattern>", Stratum.STREAM),
            arguments("variable =~ stream", "subject.name =~ subject.<test.pattern>", Stratum.STREAM),
            arguments("stream =~ variable", "subject.<test.name> =~ subject.pattern", Stratum.STREAM));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_regexWithVariables_then_evaluatesCorrectly(String description, String expression,
            Map<String, Value> variables, Value expected) {
        assertEvaluatesTo(expression, variables, expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_regexWithVariables_then_evaluatesCorrectly() {
        return Stream.of(
            arguments("variable input matches", "input =~ \"hello.*\"", Map.of("input", Value.of("hello world")), Value.TRUE),
            arguments("variable input no match", "input =~ \"hello.*\"", Map.of("input", Value.of("goodbye")), Value.FALSE),
            arguments("variable pattern", "\"hello\" =~ pattern", Map.of("pattern", Value.of("hello")), Value.TRUE),
            arguments("both variables", "input =~ pattern", Map.of("input", Value.of("abc"), "pattern", Value.of("abc")), Value.TRUE),
            arguments("non-text variable input", "input =~ \".*\"", Map.of("input", Value.of(42)), Value.FALSE));
    }
    // @formatter:on

    @Test
    void when_pureRegexWithInvalidSubscriptionPattern_then_returnsError() {
        // Use subscription element so pattern is resolved at runtime
        var subscription = AuthorizationSubscription.of(Value.NULL, Value.NULL, Value.NULL, Value.of("[invalid"));
        var evalCtx      = evaluationContext(subscription);
        var compiled     = compileExpression("\"hello\" =~ environment");
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evalCtx)).isInstanceOf(ErrorValue.class);
    }

}
