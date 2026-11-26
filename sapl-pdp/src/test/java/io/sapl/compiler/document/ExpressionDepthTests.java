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
package io.sapl.compiler.document;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.parseExpression;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.FUNCTION_BROKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Expanded expression depth")
class ExpressionDepthTests {
    private final FunctionBroker functionBroker = FUNCTION_BROKER;

    @Test
    @DisplayName("A flat chain beyond the default is refused before compilation")
    void whenFlatChainExceedsDefaultThenParsingRefusesIt() {
        val source = "policy \"p\" permit subject" + " + 1".repeat(256) + ";";
        assertThat(DocumentCompiler.parseDocument(source).diagnostics()).extracting(Diagnostic::problem)
                .containsExactly(Problem.EXPRESSION_TOO_DEEP);
    }

    static Stream<Arguments> boundaries() {
        return Stream.of(arguments("subject && action && resource", 2), arguments("subject || action || resource", 2),
                arguments("subject ^ action ^ resource", 2), arguments("subject * action * resource", 2),
                arguments("subject + 1 + 1", 3), arguments("1 + 1 + 1", 3), arguments("+ + subject", 3),
                arguments("subject.a.b", 3), arguments("subject :: @ :: @", 3), arguments("subject + (action + 1)", 3),
                arguments("[subject + 1]", 3), arguments("subject[(@ + 1)]", 3),
                arguments("subject |- { @ : filter.replace(1), @ : filter.replace(2) }", 3),
                arguments("subject |- { @.a : filter.replace(1) }", 4),
                arguments("subject |- each filter.replace(1)", 3));
    }

    @MethodSource("boundaries")
    @ParameterizedTest(name = "{0} has expanded depth {1}")
    @DisplayName("Configured limits apply to constants, runtime operands and nested chains")
    void whenBoundaryIsCrossedThenDocumentAndStandaloneParsingRefuseIt(String expression, int depth) {
        val context = new CompilationContext(functionBroker);
        val source  = "policy \"p\" permit " + expression + ";";
        for (val limit : new int[] { depth - 1, depth, depth + 1 }) {
            context.setCompilerOptions(Value.ofObject(Map.of("maxExpressionDepth", Value.of(limit))));
            if (limit < depth) {
                assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, context))
                        .isInstanceOf(SaplCompilerException.class).hasMessageContaining("Expression too deep.");
                assertThatThrownBy(() -> parseExpression(expression, limit)).isInstanceOf(SaplCompilerException.class)
                        .hasMessageContaining("Expression too deep.");
            } else {
                assertThatCode(() -> DocumentCompiler.compileDocument(source, context)).doesNotThrowAnyException();
                assertThatCode(() -> parseExpression(expression, limit)).doesNotThrowAnyException();
            }
        }
    }

    @ValueSource(strings = { "0", "-1", "1.5", "257", "4294967297", "1e100", "true", "null", "\"64\"", "[]", "{}" })
    @ParameterizedTest(name = "Invalid maxExpressionDepth {0}")
    @DisplayName("Invalid safety options fail without truncating or wrapping")
    void whenDepthOptionIsInvalidThenConfigurationFailsAndPreviousOptionSurvives(String written) {
        val context = new CompilationContext(functionBroker);
        context.setCompilerOptions(Value.ofObject(Map.of("maxExpressionDepth", Value.of(64))));
        val options = Value.ofObject(Map.of("maxExpressionDepth", ValueJsonMarshaller.json(written)));
        assertThatThrownBy(() -> context.setCompilerOptions(options)).isInstanceOf(IllegalArgumentException.class);
        assertThat(context.maxExpressionDepth()).isEqualTo(64);
    }

    @ValueSource(strings = { "1", "64", "256", "64.0", "6.4e1" })
    @ParameterizedTest(name = "Valid exact maxExpressionDepth {0}")
    @DisplayName("Exact bounded integers are accepted regardless of decimal notation")
    void whenDepthOptionIsExactThenConfigurationAcceptsIt(String written) {
        val context = new CompilationContext(functionBroker);
        val options = Value.ofObject(Map.of("maxExpressionDepth", ValueJsonMarshaller.json(written)));
        assertThatCode(() -> context.setCompilerOptions(options)).doesNotThrowAnyException();
    }

    static Stream<String> hostileExpressions() {
        return Stream.<String>builder().add("subject" + " + 1".repeat(10_000)).add("subject" + ".a".repeat(10_000))
                .add("subject |- { @ : filter.replace(1)" + ", @ : filter.replace(1)".repeat(1_000) + " }")
                .add("(subject" + " + 1".repeat(130) + ")" + " + 1".repeat(130)).build();
    }

    @MethodSource("hostileExpressions")
    @ParameterizedTest(name = "Hostile flat expression {index}")
    @DisplayName("Hostile flat chains are refused and a later document recovers")
    void whenFlatChainsAreHostileThenParsingRefusesThemAndNextDocumentRecovers(String expression) {
        val context = new CompilationContext(functionBroker);
        val source  = "policy \"p\" permit " + expression + ";";
        assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, context))
                .isInstanceOf(SaplCompilerException.class).hasMessageContaining("Expression too deep.");
        assertThatCode(() -> DocumentCompiler.compileDocument("policy \"next\" permit subject == 1;", context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Wide arrays and objects keep a shallow expression depth")
    void whenContainersAreBroadThenWidthDoesNotConsumeDepth() {
        val array  = "[subject" + ", subject".repeat(1_000) + "]";
        val object = "{a:subject" + Stream.iterate(0, i -> i + 1).limit(1_000).map(i -> ", key" + i + ":subject")
                .reduce("", String::concat) + "}";
        for (val expression : new String[] { array, object }) {
            assertThatCode(() -> parseExpression(expression, 2)).doesNotThrowAnyException();
        }
    }

    static Stream<String> defaultBoundaryExpressions() {
        return Stream.of("subject" + " + 1".repeat(255), "+ ".repeat(255) + "subject", "subject" + ".a".repeat(255),
                "subject" + " :: @".repeat(255), "subject" + " :: [@]".repeat(254),
                "subject |- { @ : filter.replace(@)" + ", @ : filter.replace(@)".repeat(254) + " }",
                "subject" + " * action".repeat(255), "1" + " + 1".repeat(255));
    }

    @MethodSource("defaultBoundaryExpressions")
    @ParameterizedTest(name = "Boundary expression {index}")
    @DisplayName("Default boundary expressions compile, evaluate and compare safely")
    void whenDepthReachesDefaultThenExpressionLifecycleIsSafe(String source) {
        val context  = compilationContext();
        val compiled = ExpressionCompiler.compile(parseExpression(source), context);
        val copy     = ExpressionCompiler.compile(parseExpression(source), context);
        if (compiled instanceof PureOperator operator && copy instanceof PureOperator other) {
            val subscription = AuthorizationSubscription.of(Value.of(1), Value.of(1), Value.of(1), Value.of(1));
            val evaluation   = evaluationContext(subscription);
            assertThat(operator).satisfies(actual -> assertThat(actual.semanticEquals(other)).isTrue(),
                    actual -> assertThat(actual.semanticHash()).isEqualTo(other.semanticHash()),
                    actual -> assertThat(actual.evaluate(evaluation)).isNotInstanceOf(ErrorValue.class));
        } else {
            assertThat(compiled).isEqualTo(copy).isInstanceOf(Value.class).isNotInstanceOf(ErrorValue.class);
        }
    }

    @ValueSource(strings = { "subject schema subject.a.b policy \"p\" permit",
            "policy \"p\" permit var x = subject.a.b;", "policy \"p\" permit var x = 1 schema subject.a.b;",
            "policy \"p\" permit obligation subject.a.b", "policy \"p\" permit advice subject.a.b",
            "policy \"p\" permit transform subject.a.b",
            "set \"s\" priority deny or abstain errors propagate for subject.a.b policy \"p\" permit",
            "set \"s\" priority deny or abstain errors propagate var x = subject.a.b; policy \"p\" permit",
            "set \"s\" priority deny or abstain errors propagate policy \"p\" permit subject.a.b;" })
    @ParameterizedTest(name = "Depth in document expression {index}")
    @DisplayName("Every document expression is validated before compilation")
    void whenAnyDocumentExpressionExceedsDepthThenDocumentCompilationRefusesIt(String source) {
        val context = new CompilationContext(functionBroker);
        context.setCompilerOptions(Value.ofObject(Map.of("maxExpressionDepth", Value.of(2))));
        assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, context))
                .isInstanceOf(SaplCompilerException.class).hasMessageContaining("Expression too deep.");
    }

}
