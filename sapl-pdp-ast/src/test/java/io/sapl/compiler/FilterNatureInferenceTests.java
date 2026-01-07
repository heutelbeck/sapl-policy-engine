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
package io.sapl.compiler;

import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.util.SimpleFunctionLibrary;
import lombok.val;
import reactor.core.publisher.Flux;

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
    private static final AttributeBroker MOCK_ATTRIBUTE_BROKER = new AttributeBroker() {
        @Override
        public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
            // Return a simple flux for any test.* attribute
            if (invocation.attributeName().startsWith("test.")) {
                return Flux.just(Value.of(42)); // Returns a number for testing
            }
            return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of();
        }
    };

    @BeforeAll
    static void setup() {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        compilationContext = new CompilationContext(functionBroker, MOCK_ATTRIBUTE_BROKER);
    }

    /**
     * Strata enumeration for test clarity.
     */
    enum Stratum {
        VALUE(1),
        PURE_NON_SUB(2),
        PURE_SUB(3),
        STREAM(4);

        final int level;

        Stratum(int level) {
            this.level = level;
        }
    }

    /**
     * Determines the expected output stratum based on input strata.
     * <ul>
     * <li>All 1 or 2 -> 1 (constant folded)</li>
     * <li>Any 3, no 4 -> 3</li>
     * <li>Any 4 -> 4</li>
     * </ul>
     */
    static Stratum expectedOutput(Stratum base, Stratum pathExpr, Stratum funcArg) {
        int maxLevel = Math.max(Math.max(base.level, pathExpr.level), funcArg.level);
        if (maxLevel <= 2) {
            return Stratum.VALUE; // Constant folded
        }
        return maxLevel == 3 ? Stratum.PURE_SUB : Stratum.STREAM;
    }

    /**
     * Asserts that a compiled expression matches the expected stratum.
     */
    static void assertStratum(CompiledExpression compiled, Stratum expected) {
        switch (expected) {
        case VALUE        -> assertThat(compiled).isInstanceOf(Value.class);
        case PURE_NON_SUB -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isFalse();
        }
        case PURE_SUB     -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
        case STREAM       -> assertThat(compiled).isInstanceOf(StreamOperator.class);
        }
    }

    /**
     * Compiles expression and returns stratum, or null if error.
     */
    static Stratum getStratum(CompiledExpression compiled) {
        if (compiled instanceof ErrorValue) {
            return null;
        }
        if (compiled instanceof Value) {
            return Stratum.VALUE;
        }
        if (compiled instanceof PureOperator p) {
            return p.isDependingOnSubscription() ? Stratum.PURE_SUB : Stratum.PURE_NON_SUB;
        }
        if (compiled instanceof StreamOperator) {
            return Stratum.STREAM;
        }
        return null;
    }

    // =========================================================================
    // SIMPLE FILTER TESTS: BASE |- func(ARG)
    // =========================================================================

    @Nested
    @DisplayName("Simple Filter: base |- func(arg)")
    class SimpleFilterTests {

        // Base expressions for each stratum
        // Note: Strata 2 (Pure-non-sub) not naturally available at top level
        static final String BASE_VALUE    = "42";
        static final String BASE_PURE_SUB = "subject.value";
        static final String BASE_STREAM   = "subject.<test.number>";

        // Function argument expressions for each stratum
        static final String ARG_VALUE        = "1";
        static final String ARG_PURE_NON_SUB = "@"; // @ is available in function context? No, not in simple filter
        static final String ARG_PURE_SUB     = "subject.multiplier";
        static final String ARG_STREAM       = "subject.<test.multiplier>";

        @Test
        @DisplayName("Value base + Value arg -> Value (constant folded)")
        void base_value_arg_value() {
            val compiled = compileExpression("42 |- simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.VALUE);
        }

        @Test
        @DisplayName("Value base + Pure-sub arg -> Pure-sub")
        void base_value_arg_pureSub() {
            // simple.addValue(base, arg) - if arg is subject.x, result is Pure-sub
            val compiled = compileExpression("42 |- simple.addValue(subject.offset)", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Value base + Stream arg -> Stream")
        void base_value_arg_stream() {
            val compiled = compileExpression("42 |- simple.addValue(subject.<test.offset>)", compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Pure-sub base + Value arg -> Pure-sub")
        void base_pureSub_arg_value() {
            val compiled = compileExpression("subject.value |- simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Pure-sub base + Pure-sub arg -> Pure-sub")
        void base_pureSub_arg_pureSub() {
            val compiled = compileExpression("subject.value |- simple.addValue(subject.offset)", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Pure-sub base + Stream arg -> Stream")
        void base_pureSub_arg_stream() {
            val compiled = compileExpression("subject.value |- simple.addValue(subject.<test.offset>)",
                    compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Stream base + Value arg -> Stream")
        void base_stream_arg_value() {
            val compiled = compileExpression("subject.<test.value> |- simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Stream base + Pure-sub arg -> Stream")
        void base_stream_arg_pureSub() {
            val compiled = compileExpression("subject.<test.value> |- simple.addValue(subject.offset)",
                    compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Stream base + Stream arg -> Stream")
        void base_stream_arg_stream() {
            val compiled = compileExpression("subject.<test.value> |- simple.addValue(subject.<test.offset>)",
                    compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }
    }

    // =========================================================================
    // EXTENDED FILTER TESTS: BASE |- { @[(PATH_EXPR)] : func(ARG) }
    // =========================================================================

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

            return Arguments.of(base[0], baseStratum, pathExpr[0], pathExprStratum, funcArg[0], funcArgStratum,
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
        @DisplayName("Stream in path expression -> StreamOperator")
        void streamInPathExpr_producesStreamOp() {
            // Stream in path is now correctly detected at compile time
            val compiled = compileExpression("[1, 2, 3] |- { @[(subject.<test.index>)] : simple.identity }",
                    compilationContext);
            // Returns StreamOperator because path contains stream
            assertThat(compiled).isInstanceOf(StreamOperator.class);
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

    // =========================================================================
    // EACH FILTER TESTS: BASE |- each func(ARG)
    // =========================================================================

    @Nested
    @DisplayName("Each Filter: base |- each func(arg)")
    class EachFilterTests {

        @Test
        @DisplayName("Value base + Value arg -> Value (constant folded)")
        void base_value_arg_value() {
            val compiled = compileExpression("[1, 2, 3] |- each simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.VALUE);
        }

        @Test
        @DisplayName("Value base + Pure-sub arg -> Pure-sub")
        void base_value_arg_pureSub() {
            val compiled = compileExpression("[1, 2, 3] |- each simple.addValue(subject.offset)", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Value base + Stream arg -> Stream")
        void base_value_arg_stream() {
            val compiled = compileExpression("[1, 2, 3] |- each simple.addValue(subject.<test.offset>)",
                    compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Pure-sub base + Value arg -> Pure-sub")
        void base_pureSub_arg_value() {
            val compiled = compileExpression("subject.items |- each simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Stream base + Value arg -> Stream")
        void base_stream_arg_value() {
            val compiled = compileExpression("subject.<test.items> |- each simple.doubleValue", compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Stream base + Stream arg -> Stream")
        void base_stream_arg_stream() {
            val compiled = compileExpression("subject.<test.items> |- each simple.addValue(subject.<test.offset>)",
                    compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }
    }

    // =========================================================================
    // STRATA VERIFICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Strata Verification")
    class StrataVerificationTests {

        @Test
        @DisplayName("Literal is Value (strata 1)")
        void literal_isValue() {
            val compiled = compileExpression("42", compilationContext);
            assertStratum(compiled, Stratum.VALUE);
        }

        @Test
        @DisplayName("Identifier is Pure-sub (strata 3)")
        void identifier_isPureSub() {
            val compiled = compileExpression("subject", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Identifier field access is Pure-sub (strata 3)")
        void identifierField_isPureSub() {
            val compiled = compileExpression("subject.name", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Attribute access is Stream (strata 4)")
        void attributeAccess_isStream() {
            val compiled = compileExpression("subject.<test.attr>", compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }

        @Test
        @DisplayName("Function with all Value args -> Value (constant folded)")
        void functionAllValueArgs_isValue() {
            val compiled = compileExpression("simple.doubleValue(21)", compilationContext);
            assertStratum(compiled, Stratum.VALUE);
        }

        @Test
        @DisplayName("Function with Pure-sub arg -> Pure-sub")
        void functionPureSubArg_isPureSub() {
            val compiled = compileExpression("simple.doubleValue(subject.value)", compilationContext);
            assertStratum(compiled, Stratum.PURE_SUB);
        }

        @Test
        @DisplayName("Function with Stream arg -> Stream")
        void functionStreamArg_isStream() {
            val compiled = compileExpression("simple.doubleValue(subject.<test.value>)", compilationContext);
            assertStratum(compiled, Stratum.STREAM);
        }
    }
}
