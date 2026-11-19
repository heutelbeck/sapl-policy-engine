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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The cases of the operator '=~' that every implementation of SAPL shares. The
 * pattern must match the whole text, is matched in linear time without
 * backtracking, and constructs that need backtracking are refused as invalid.
 * <p>
 * The outcome is written as in the shared evaluation corpus: what the
 * expression compiles to, followed by its value. Where this engine differs
 * from the other implementations, the case says so in its comment.
 */
@DisplayName("The operator '=~' as all implementations of SAPL evaluate it")
class RegexConformanceTests {

    private static final Value NO_INPUT = Value.UNDEFINED;

    @Timeout(10)
    @MethodSource
    @ParameterizedTest(name = "{0}")
    void whenMatchingThenOutcomeIsAsInTheSharedCorpus(String heading, String expression, Value subject, Value action,
            String expectedOutcome) {
        assertThat(outcomeOf(expression, subject, action)).isEqualTo(expectedOutcome);
    }

    // @formatter:off
    private static Stream<Arguments> whenMatchingThenOutcomeIsAsInTheSharedCorpus() {
        return Stream.of(
            arguments("a text matching itself", "\"hello\" =~ \"hello\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("a text not matching another text", "\"hello\" =~ \"world\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("a text matching only at its start", "\"hello\" =~ \"hel\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("a text matching any continuation", "\"hello\" =~ \"h.*\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("letters and digits", "\"abc123\" =~ \"[a-z]+[0-9]+\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("an alternative that matches only if the second is taken", "\"ab\" =~ \"a|ab\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("digits matched with a class", "\"123\" =~ \"\\\\d+\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("a line feed matched by a dot", "\"a\\nb\" =~ \"a.b\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("a line feed matched by a dot in single line mode", "\"a\\nb\" =~ \"(?s)a.b\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("a capital letter matched without regard to case", "\"A\" =~ \"(?i)a\"", NO_INPUT, NO_INPUT, "constant: true"),
            arguments("a number", "5 =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("a boolean", "true =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("null", "null =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("an empty array", "[] =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("an empty object", "{} =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("undefined", "undefined =~ \".*\"", NO_INPUT, NO_INPUT, "constant: false"),
            arguments("a number as pattern", "\"a\" =~ 1", NO_INPUT, NO_INPUT,
                    "constant error: Regular expression must be a string, but got: 1."),
            arguments("null as pattern", "\"a\" =~ null", NO_INPUT, NO_INPUT,
                    "constant error: Regular expression must be a string, but got: null."),
            arguments("undefined as pattern", "\"a\" =~ undefined", NO_INPUT, NO_INPUT,
                    "constant error: Regular expression must be a string, but got: undefined."),
            arguments("a number matched against a number as pattern", "5 =~ 1", NO_INPUT, NO_INPUT,
                    "constant error: Regular expression must be a string, but got: 1."),
            arguments("the subject matched against a number as pattern", "subject =~ 1", Value.of("a"), NO_INPUT,
                    "constant error: Regular expression must be a string, but got: 1."),
            arguments("a division by zero", "(1 / 0) =~ \"a\"", NO_INPUT, NO_INPUT, "constant error: Division by zero."),
            arguments("a division by zero as pattern", "\"a\" =~ (1 / 0)", NO_INPUT, NO_INPUT, "constant error: Division by zero."),
            arguments("a division by zero matched against a number as pattern", "(1 / 0) =~ 1", NO_INPUT, NO_INPUT,
                    "constant error: Division by zero."),
            arguments("the subject matching a pattern", "subject =~ \"a.*\"", Value.of("abc"), NO_INPUT, "pure: true"),
            arguments("the subject not matching a pattern", "subject =~ \"a.*\"", Value.of("xyz"), NO_INPUT, "pure: false"),
            arguments("a subject that is a number", "subject =~ \"a.*\"", Value.of(5), NO_INPUT, "pure: false"),
            arguments("a text matching the subject as pattern", "\"abc\" =~ subject", Value.of("a.c"), NO_INPUT, "pure: true"),
            arguments("the subject as pattern that is a number", "\"abc\" =~ subject", Value.of(1), NO_INPUT,
                    "pure error: Regular expression must be a string, but got: 1."),
            arguments("the subject as pattern that is a number, matched against a number", "1 =~ subject", Value.of(1), NO_INPUT,
                    "pure error: Regular expression must be a string, but got: 1."),
            arguments("the subject matching the action as pattern", "subject =~ action", Value.of("alice"), Value.of("a.*e"),
                    "pure: true"),
            // This engine refuses an invalid constant pattern while compiling where the
            // other implementations yield a constant error. The reason is that of RE2/J.
            arguments("an unclosed group", "\"a\" =~ \"(\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression '(': missing closing )."),
            arguments("the subject as pattern that is an unclosed group", "\"a\" =~ subject", Value.of("("), NO_INPUT,
                    "pure error: Invalid regular expression '(': missing closing )."),
            arguments("a look-ahead", "\"ab\" =~ \"a(?=b)b\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression 'a(?=b)b': invalid or unsupported Perl syntax."),
            arguments("a look-behind", "\"ab\" =~ \"a(?<=a)b\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression 'a(?<=a)b': invalid named capture."),
            arguments("a backreference", "\"aa\" =~ \"(a)\\\\1\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression '(a)\\1': invalid escape sequence."),
            arguments("a possessive quantifier", "\"aaa\" =~ \"a*+a\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression 'a*+a': invalid nested repetition operator."),
            arguments("an atomic group", "\"a\" =~ \"(?>a)\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression '(?>a)': invalid or unsupported Perl syntax."),
            // RE2/J reads a quoted section, the other implementations refuse it.
            arguments("a quoted section", "\"a.b\" =~ \"\\\\Qa.b\\\\E\"", NO_INPUT, NO_INPUT, "constant: true"),
            // The classes of RE2/J hold ASCII characters only, those of the other
            // implementations follow Unicode.
            arguments("a letter with an accent matched by the class of word characters", "\"\\u00e9\" =~ \"\\\\w\"", NO_INPUT, NO_INPUT,
                    "constant: false"),
            arguments("an Arabic digit matched by the class of digits", "\"\\u0663\" =~ \"\\\\d\"", NO_INPUT, NO_INPUT,
                    "constant: false"),
            arguments("a capital letter with an accent matched without regard to case", "\"\\u00c9\" =~ \"(?i)\\u00e9\"", NO_INPUT, NO_INPUT,
                    "constant: true"),
            arguments("nested repetitions of a thousand each", "\"a\" =~ \"(?:a{1000}){1000}\"", NO_INPUT, NO_INPUT,
                    "refused: Invalid regular expression '(?:a{1000}){1000}': expression too large."),
            arguments("a nested repetition on a text that almost matches", "\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab\" =~ \"(a+)+c\"",
                    NO_INPUT, NO_INPUT, "constant: false"));
    }
    // @formatter:on

    private static String outcomeOf(String expression, Value subject, Value action) {
        final CompiledExpression compiled;
        try {
            compiled = ExpressionCompiler.compile(parseExpression(expression), compilationContext());
        } catch (SaplCompilerException refusal) {
            return "refused: " + refusal.getMessage();
        }
        return switch (compiled) {
        case Value value               -> "constant" + written(value);
        case PureOperator pureOperator -> "pure" + written(pureOperator.evaluate(evaluationContext(
                new AuthorizationSubscription(subject, action, NO_INPUT, NO_INPUT, Value.EMPTY_OBJECT))));
        case StreamOperator ignored    -> "streaming";
        };
    }

    private static String written(Value value) {
        if (value instanceof ErrorValue(var message, var ignoredCause, var ignoredLocation)) {
            return " error: " + message;
        }
        val json = ValueJsonMarshaller.toJsonString(value);
        return ": " + json;
    }
}
