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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.compiler.util.Stratum;
import io.sapl.util.SimpleFunctionLibrary;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.assertStratum;
import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static io.sapl.util.TestBrokers.attributeBroker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test matrix for filter expression nature/strata inference.
 * <p>
 * Tests all combinations of strata for the three sub-expression positions in
 * filter expressions:
 * <ul>
 * <li><b>Base</b>: The value being filtered ({@code BASE |- ...})</li>
 * <li><b>Path Expression</b>: Dynamic path index ({@code @[(EXPR)]})</li>
 * <li><b>Function Argument</b>: Arguments to filter function
 * ({@code func(ARG)})</li>
 * </ul>
 * <p>
 * <b>Strata Levels:</b>
 * <ol>
 * <li><b>Value</b>: Compile-time constant</li>
 * <li><b>Pure-non-sub</b>: Runtime, {@code isDependingOnSubscription()=false}
 * (uses @ or #)</li>
 * <li><b>Pure-sub</b>: Runtime, {@code isDependingOnSubscription()=true} (uses
 * subject/action/etc)</li>
 * <li><b>Stream</b>: Reactive {@code StreamOperator} (attribute access)</li>
 * </ol>
 * <p>
 * <b>Expected Output Rule (IDEAL):</b>
 * <ul>
 * <li>If all inputs are strata 1 or 2 -> output is strata 1 (constant
 * folded)</li>
 * <li>If any input is strata 3 (no strata 4) -> output is strata 3</li>
 * <li>If any input is strata 4 -> output is strata 4</li>
 * </ul>
 * <p>
 * <b>CURRENT BEHAVIOR (implementation gaps):</b>
 * <ul>
 * <li>Extended filters with ExpressionPath ALWAYS produce PureOperator (no
 * constant folding even if all values are constants)</li>
 * <li>Stream detection in path expressions happens at runtime, not compile
 * time</li>
 * <li>Simple filters and each filters DO constant-fold when all inputs are
 * Values</li>
 * </ul>
 */
@DisplayName("Filter Nature Inference")
class FilterNatureInferenceTests {

    private static CompilationContext compilationContext;

    /**
     * Mock attribute broker that returns streams for test.* attributes.
     */
    private static final AttributeBroker MOCK_ATTRIBUTE_BROKER = attributeBroker(invocation -> {
        // Return a simple flux for any test.* attribute
        if (invocation.attributeName().startsWith("test.")) {
            return Flux.just(Value.of(42)); // Returns a number for testing
        }
        return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
    });

    @BeforeAll
    static void setup() {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        compilationContext = new CompilationContext(functionBroker, MOCK_ATTRIBUTE_BROKER);
    }

    @Nested
    @DisplayName("Simple Filter: base |- func(arg)")
    class SimpleFilterTests {

        @ParameterizedTest(name = "base={0}, arg={2} -> {4}")
        @MethodSource("simpleFilterCombinations")
        void simpleFilter_strataMatrix(String baseExpr, Stratum baseStratum, String argExpr, Stratum argStratum,
                Stratum expectedStratum) {
            var expression = argStratum == Stratum.VALUE ? baseExpr + " |- simple.doubleValue"
                    : baseExpr + " |- simple.addValue(" + argExpr + ")";
            val compiled   = compileExpression(expression, compilationContext);
            assertStratum(compiled, expectedStratum);
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

    /**
     * Tests for extended filters with expression paths.
     * <p>
     * <b>CURRENT BEHAVIOR (implementation gaps):</b>
     * <ul>
     * <li>Extended filters with ExpressionPath ALWAYS produce PureOperator - no
     * constant folding even when all inputs are Values</li>
     * <li>Stream in path expression does NOT produce compile-time error; it's
     * detected at runtime</li>
     * <li>isDependingOnSubscription() is based on path and args, not the base
     * value</li>
     * </ul>
     * <p>
     * <b>IDEAL BEHAVIOR:</b>
     * <ul>
     * <li>All strata 1+2 inputs -> Value (constant folded)</li>
     * <li>Stream in path expression -> compile-time Error</li>
     * </ul>
     */
    @Nested
    @DisplayName("Extended Filter: base |- { @[(pathExpr)] : func(arg) }")
    class ExtendedFilterTests {

        /**
         * Test matrix for extended filters with expression paths.
         * <p>
         * Tests CURRENT behavior: ExpressionPath always produces PureOperator, with
         * isDependingOnSubscription based on path+args (not base).
         */
        @ParameterizedTest(name = "base={0}, pathExpr={1}, funcArg={2} -> current={3}")
        @MethodSource("extendedFilterCombinations")
        void extendedFilter_strataMatrix_currentBehavior(String baseExpr, Stratum baseStratum, String pathExprExpr,
                Stratum pathExprStratum, String funcArgExpr, Stratum funcArgStratum, Stratum currentExpectedStratum) {
            // Build expression: BASE |- { @[(pathExpr)] : simple.addValue(funcArg) }
            val expression = String.format("%s |- { @[(%s)] : simple.addValue(%s) }", baseExpr, pathExprExpr,
                    funcArgExpr);
            val compiled   = compileExpression(expression, compilationContext);

            assertStratum(compiled, currentExpectedStratum);
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

            // CURRENT BEHAVIOR: Extended filter with ExpressionPath always yields
            // PureOperator
            // - If any component (base, path, arg) is STREAM -> STREAM
            // - Otherwise -> PURE_NON_SUB or PURE_SUB based on path+args (not base!)
            val currentExpected = computeCurrentBehavior(baseStratum, pathExprStratum, funcArgStratum);

            return arguments(base[0], baseStratum, pathExpr[0], pathExprStratum, funcArg[0], funcArgStratum,
                    currentExpected);
        }

        /**
         * Computes CURRENT implementation behavior for extended filters.
         * <p>
         * The new implementation:
         * <ul>
         * <li>Correctly detects streams in path expressions</li>
         * <li>Checks path subscription dependency in isDependingOnSubscription()</li>
         * <li>Constant-folds when all inputs are Values</li>
         * </ul>
         */
        private static Stratum computeCurrentBehavior(Stratum base, Stratum pathExpr, Stratum funcArg) {
            // If any is STREAM -> STREAM
            if (base == Stratum.STREAM || pathExpr == Stratum.STREAM || funcArg == Stratum.STREAM) {
                return Stratum.STREAM;
            }
            // Path's subscription dependency is now correctly checked
            if (base == Stratum.PURE_SUB || funcArg == Stratum.PURE_SUB || pathExpr == Stratum.PURE_SUB) {
                return Stratum.PURE_SUB;
            }
            // All inputs are VALUE -> constant fold to VALUE
            if (base == Stratum.VALUE && pathExpr == Stratum.VALUE && funcArg == Stratum.VALUE) {
                return Stratum.VALUE;
            }
            return Stratum.PURE_NON_SUB;
        }

        @Test
        @DisplayName("Stream in path expression -> throws compile-time exception")
        void streamInPathExpr_throwsCompileException() {
            // Stream in path is disallowed at compile time
            assertThatThrownBy(() -> compileExpression("[1, 2, 3] |- { @[(subject.<test.index>)] : simple.identity }",
                    compilationContext)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Stream operators not allowed in filter path");
        }

        @Test
        @DisplayName("All Value inputs are constant-folded")
        void allValueInputs_constantFolded() {
            // All inputs are Values, so the expression is constant-folded
            val compiled = compileExpression("[1, 2, 3] |- { @[(0)] : simple.addValue(1) }", compilationContext);

            // Now correctly constant-folds to a Value
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @Test
        @DisplayName("Path subscription dependency correctly checked in isDependingOnSubscription()")
        void pathSubscriptionDependency_correctlyChecked() {
            // Path expression (subject.index) depends on subscription
            val compiled = compileExpression("[1, 2, 3] |- { @[(subject.index)] : simple.addValue(1) }",
                    compilationContext);

            assertThat(compiled).isInstanceOf(PureOperator.class);
            val pureOp = (PureOperator) compiled;

            // Path subscription dependency is now correctly detected
            assertThat(pureOp.isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    @DisplayName("Each Filter: base |- each func(arg)")
    class EachFilterTests {

        @ParameterizedTest(name = "base={0}, arg={2} -> {4}")
        @MethodSource("eachFilterCombinations")
        void eachFilter_strataMatrix(String baseExpr, Stratum baseStratum, String argExpr, Stratum argStratum,
                Stratum expectedStratum) {
            var expression = argStratum == Stratum.VALUE ? baseExpr + " |- each simple.doubleValue"
                    : baseExpr + " |- each simple.addValue(" + argExpr + ")";
            val compiled   = compileExpression(expression, compilationContext);
            assertStratum(compiled, expectedStratum);
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
        void expression_hasExpectedStratum(String description, String expression, Stratum expectedStratum) {
            val compiled = compileExpression(expression, compilationContext);
            assertStratum(compiled, expectedStratum);
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
