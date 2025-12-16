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
package io.sapl.lsp.sapl.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.lsp.sapl.completion.ContextAnalyzer.ProposalType;

/**
 * Tests for ContextAnalyzer detecting function/attribute contexts.
 */
class ContextAnalyzerTests {

    static Stream<Arguments> functionContextTestCases() {
        return Stream.of(
                arguments("Simple no-arg function call", "policy \"test\" permit where time.now().", "time.now"),
                arguments("Function with single argument", "policy \"test\" permit where time.dayOfWeekFrom(now).",
                        "time.dayOfWeekFrom"),
                arguments("Simple function without namespace", "policy \"test\" permit where foo().", "foo"),
                arguments("Nested function calls - outermost detected", "policy \"test\" permit where outer(inner()).",
                        "outer"),
                arguments("Function with multiple arguments",
                        "policy \"test\" permit where format(template, arg1, arg2).", "format"),
                arguments("Chained function calls - last function detected",
                        "policy \"test\" permit where first().second().", "second"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("functionContextTestCases")
    void whenCursorAfterFunctionCall_thenFunctionTypeAndNameDetected(String description, String document,
            String expectedFunctionName) {
        var result = analyzeAtEnd(document);

        assertThat(result.type()).isEqualTo(ProposalType.FUNCTION);
        assertThat(result.functionName()).isEqualTo(expectedFunctionName);
    }

    static Stream<Arguments> attributeContextTestCases() {
        return Stream.of(
                arguments("Regular attribute after subject", "policy \"test\" permit where subject.<auth.user>.",
                        "auth.user", ProposalType.ATTRIBUTE),
                arguments("Environment attribute with pipe", "policy \"test\" permit where |<clock.now>.", "clock.now",
                        ProposalType.ENVIRONMENT_ATTRIBUTE),
                arguments("Attribute with arguments", "policy \"test\" permit where subject.<db.query(sql)>.",
                        "db.query", ProposalType.ATTRIBUTE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attributeContextTestCases")
    void whenCursorAfterAttribute_thenAttributeTypeAndNameDetected(String description, String document,
            String expectedName, ProposalType expectedType) {
        var result = analyzeAtEnd(document);

        assertThat(result.type()).isEqualTo(expectedType);
        assertThat(result.functionName()).isEqualTo(expectedName);
    }

    static Stream<Arguments> typingAfterDotTestCases() {
        return Stream.of(
                arguments("Typing after function call dot", "policy \"test\" permit where time.now().ye",
                        ProposalType.FUNCTION, "time.now", "ye"),
                arguments("Typing after attribute dot", "policy \"test\" permit where subject.<auth.user>.na",
                        ProposalType.ATTRIBUTE, "auth.user", "na"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("typingAfterDotTestCases")
    void whenTypingAfterDot_thenContextAndPrefixDetected(String description, String document, ProposalType expectedType,
            String expectedFunctionName, String expectedPrefix) {
        var result = analyzeAtEnd(document);

        assertThat(result.type()).isEqualTo(expectedType);
        assertThat(result.functionName()).isEqualTo(expectedFunctionName);
        assertThat(result.ctxPrefix()).isEqualTo(expectedPrefix);
    }

    @Test
    void whenCursorAtSimpleDot_thenVariableOrFunctionType() {
        var result = analyzeAtEnd("policy \"test\" permit where subject.");

        assertThat(result.type()).isEqualTo(ProposalType.VARIABLE_OR_FUNCTION_NAME);
    }

    @Test
    void whenCursorTypingIdentifier_thenVariableOrFunctionType() {
        var result = analyzeAtEnd("policy \"test\" permit where sub");

        assertThat(result.type()).isEqualTo(ProposalType.VARIABLE_OR_FUNCTION_NAME);
    }

    @Test
    void whenEmptyDocument_thenIndeterminateType() {
        var result = ContextAnalyzer.analyze(tokenize(""), new Position(0, 0));

        assertThat(result.type()).isEqualTo(ProposalType.INDETERMINATE);
    }

    private static ContextAnalyzer.ContextAnalysisResult analyzeAtEnd(String document) {
        return ContextAnalyzer.analyze(tokenize(document), positionAtEnd(document));
    }

    private static List<Token> tokenize(String document) {
        var charStream  = CharStreams.fromString(document);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        return new ArrayList<>(tokenStream.getTokens());
    }

    private static Position positionAtEnd(String document) {
        var lines = document.split("\n", -1);
        var line  = lines.length - 1;
        var col   = lines[line].length();
        return new Position(line, col);
    }

}
