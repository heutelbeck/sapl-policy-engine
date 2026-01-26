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

import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.util.Stratum;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.assertStratumOfCompiledExpression;
import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Filter Nature Inference")
class FilterNatureInferenceTests {

    @Nested
    @DisplayName("Simple Filter: base |- func(arg)")
    class SimpleFilterTests {

        @ParameterizedTest(name = "base={0}, arg={2} -> {4}")
        @MethodSource("simpleFilterCombinations")
        void simpleFilterStrataMatrix(String baseExpr, Stratum baseStratum, String argExpr, Stratum argStratum,
                Stratum expectedStratum) {
            var expression = argStratum == Stratum.VALUE ? baseExpr + " |- simple.doubleValue"
                    : baseExpr + " |- simple.addValue(" + argExpr + ")";
            assertStratumOfCompiledExpression(expression, expectedStratum);
        }

        static Stream<Arguments> simpleFilterCombinations() {
            return Stream.of(
                    // Value base
                    arguments("42", Stratum.VALUE, "", Stratum.VALUE, Stratum.VALUE),
                    arguments("42", Stratum.VALUE, "subject.offset", Stratum.PURE_SUB, Stratum.PURE_SUB),
                    arguments("42", Stratum.VALUE, "subject.<test.offset>", Stratum.STREAM, Stratum.STREAM),
                    // Pure-sub base
                    arguments("subject.value", Stratum.PURE_SUB, "", Stratum.VALUE, Stratum.PURE_SUB),
                    arguments("subject.value", Stratum.PURE_SUB, "subject.offset", Stratum.PURE_SUB, Stratum.PURE_SUB),
                    arguments("subject.value", Stratum.PURE_SUB, "subject.<test.offset>", Stratum.STREAM,
                            Stratum.STREAM),
                    // Stream base
                    arguments("subject.<test.value>", Stratum.STREAM, "", Stratum.VALUE, Stratum.STREAM),
                    arguments("subject.<test.value>", Stratum.STREAM, "subject.offset", Stratum.PURE_SUB,
                            Stratum.STREAM),
                    arguments("subject.<test.value>", Stratum.STREAM, "subject.<test.offset>", Stratum.STREAM,
                            Stratum.STREAM));
        }
    }

    @Nested
    @DisplayName("Extended Filter: base |- { @[(pathExpr)] : func(arg) }")
    class ExtendedFilterTests {

        @ParameterizedTest(name = "base={0}, pathExpr={2}, funcArg={4} -> {6}")
        @MethodSource("extendedFilterCombinations")
        void extendedFilterStrataMatrix(String baseExpr, Stratum baseStratum, String pathExprExpr,
                Stratum pathExprStratum, String funcArgExpr, Stratum funcArgStratum, Stratum expectedStratum) {
            val expression = String.format("%s |- { @[(%s)] : simple.addValue(%s) }", baseExpr, pathExprExpr,
                    funcArgExpr);
            assertStratumOfCompiledExpression(expression, expectedStratum);
        }

        static Stream<Arguments> extendedFilterCombinations() {
            // Base expressions (strata 1, 3, 4)
            String[][] bases = { { "[1, 2, 3]", "VALUE" }, { "subject.items", "PURE_SUB" },
                    { "subject.<test.items>", "STREAM" } };

            // Path expressions (strata 1, 3)
            String[][] pathExprs = { { "0", "VALUE" }, { "subject.index", "PURE_SUB" } };

            // Function argument expressions (strata 1, 3, 4)
            String[][] funcArgs = { { "1", "VALUE" }, { "subject.offset", "PURE_SUB" },
                    { "subject.<test.offset>", "STREAM" } };

            return Stream.of(bases).flatMap(base -> Stream.of(pathExprs)
                    .flatMap(pathExpr -> Stream.of(funcArgs).map(funcArg -> createArgs(base, pathExpr, funcArg))));
        }

        private static Arguments createArgs(String[] base, String[] pathExpr, String[] funcArg) {
            val baseStratum     = Stratum.valueOf(base[1]);
            val pathExprStratum = Stratum.valueOf(pathExpr[1]);
            val funcArgStratum  = Stratum.valueOf(funcArg[1]);
            val expected        = computeExpectedStratum(baseStratum, pathExprStratum, funcArgStratum);
            return arguments(base[0], baseStratum, pathExpr[0], pathExprStratum, funcArg[0], funcArgStratum, expected);
        }

        private static Stratum computeExpectedStratum(Stratum base, Stratum pathExpr, Stratum funcArg) {
            if (base == Stratum.STREAM || pathExpr == Stratum.STREAM || funcArg == Stratum.STREAM) {
                return Stratum.STREAM;
            }
            if (base == Stratum.PURE_SUB || funcArg == Stratum.PURE_SUB || pathExpr == Stratum.PURE_SUB) {
                return Stratum.PURE_SUB;
            }
            if (base == Stratum.VALUE && pathExpr == Stratum.VALUE && funcArg == Stratum.VALUE) {
                return Stratum.VALUE;
            }
            return Stratum.PURE_NON_SUB;
        }

        @Test
        @DisplayName("Stream in path expression -> throws compile-time exception")
        void streamInPathExprThrowsCompileException() {
            assertThatThrownBy(
                    () -> compileExpression("[1, 2, 3] |- { @[(subject.<test.index>)] : filter.replace(\"***\") }"))
                    .isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Stream operators not allowed in filter path");
        }

        @Test
        @DisplayName("All Value inputs are constant-folded")
        void allValueInputsConstantFolded() {
            val compiled = compileExpression("[1, 2, 3] |- { @[(0)] : simple.addValue(1) }");
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @Test
        @DisplayName("Path subscription dependency correctly checked in isDependingOnSubscription()")
        void pathSubscriptionDependencyCorrectlyChecked() {
            val compiled = compileExpression("[1, 2, 3] |- { @[(subject.index)] : simple.addValue(1) }");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    @DisplayName("Each Filter: base |- each func(arg)")
    class EachFilterTests {

        @ParameterizedTest(name = "base={0}, arg={2} -> {4}")
        @MethodSource("eachFilterCombinations")
        void eachFilterStrataMatrix(String baseExpr, Stratum baseStratum, String argExpr, Stratum argStratum,
                Stratum expectedStratum) {
            var expression = argStratum == Stratum.VALUE ? baseExpr + " |- each simple.doubleValue"
                    : baseExpr + " |- each simple.addValue(" + argExpr + ")";
            assertStratumOfCompiledExpression(expression, expectedStratum);
        }

        static Stream<Arguments> eachFilterCombinations() {
            return Stream.of(
                    // Value base
                    arguments("[1, 2, 3]", Stratum.VALUE, "", Stratum.VALUE, Stratum.VALUE),
                    arguments("[1, 2, 3]", Stratum.VALUE, "subject.offset", Stratum.PURE_SUB, Stratum.PURE_SUB),
                    arguments("[1, 2, 3]", Stratum.VALUE, "subject.<test.offset>", Stratum.STREAM, Stratum.STREAM),
                    // Pure-sub base
                    arguments("subject.items", Stratum.PURE_SUB, "", Stratum.VALUE, Stratum.PURE_SUB),
                    // Stream base
                    arguments("subject.<test.items>", Stratum.STREAM, "", Stratum.VALUE, Stratum.STREAM),
                    arguments("subject.<test.items>", Stratum.STREAM, "subject.<test.offset>", Stratum.STREAM,
                            Stratum.STREAM));
        }
    }

    @Nested
    @DisplayName("Strata Verification")
    class StrataVerificationTests {

        @ParameterizedTest(name = "{0} -> {2}")
        @MethodSource("strataVerificationCases")
        void expressionHasExpectedStratum(String description, String expression, Stratum expectedStratum) {
            assertStratumOfCompiledExpression(expression, expectedStratum);
        }

        static Stream<Arguments> strataVerificationCases() {
            return Stream.of(arguments("Literal", "42", Stratum.VALUE),
                    arguments("Identifier", "subject", Stratum.PURE_SUB),
                    arguments("Identifier field access", "subject.name", Stratum.PURE_SUB),
                    arguments("Attribute access", "subject.<test.attr>", Stratum.STREAM),
                    arguments("Function with Value args", "simple.doubleValue(21)", Stratum.VALUE),
                    arguments("Function with Pure-sub arg", "simple.doubleValue(subject.value)", Stratum.PURE_SUB),
                    arguments("Function with Stream arg", "simple.doubleValue(subject.<test.value>)", Stratum.STREAM));
        }
    }
}
