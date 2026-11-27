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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.sapl.util.SaplTesting.FUNCTION_BROKER;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

@DisplayName("in-array unrolling")
class InArrayUnrollingCompilerTests {

    private static CompilationContext unrollingContext() {
        return unrollingContext(new CompilationContext(FUNCTION_BROKER));
    }

    private static CompilationContext unrollingContext(CompilationContext ctx) {
        ctx.setCompilerOptions(Value.ofObject(Map.of(CompilationContext.OPTION_UNROLL_IN_OPERATOR, Value.TRUE)));
        return ctx;
    }

    @Nested
    @DisplayName("empty array")
    class EmptyArray {

        @Test
        @DisplayName("non-erroring needle in [] folds to false")
        void whenNonErroringNeedleInEmptyArrayThenFalse() {
            val compiled = compileExpression("\"a\" in []", unrollingContext());
            assertThat(compiled).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("compile-time erroring needle in [] propagates the error rather than folding to false")
        void whenCompileTimeErroringNeedleInEmptyArrayThenErrorPropagates() {
            val compiled = compileExpression("(10/0) in []", unrollingContext());
            assertThat(compiled).asInstanceOf(type(ErrorValue.class)).extracting(ErrorValue::message).asString()
                    .containsIgnoringCase("zero");
        }

        @Test
        @DisplayName("runtime erroring needle in [] propagates the error rather than folding to false")
        void whenRuntimeErroringNeedleInEmptyArrayThenErrorPropagates() {
            // A subscription-dependent needle stays a PureOperator, so the empty-array
            // branch must propagate the runtime error rather than fold to false.
            val ast      = parseExpression("(10/subject.x) in []");
            val compiled = ExpressionCompiler.compile(ast, unrollingContext());

            assertThat(compiled).isInstanceOf(PureOperator.class);

            val subject      = ObjectValue.builder().put("x", Value.of(0)).build();
            val subscription = AuthorizationSubscription.of(subject, Value.NULL, Value.NULL, Value.NULL);
            val result       = ((PureOperator) compiled).evaluate(evaluationContext(subscription));

            assertThat(result).asInstanceOf(type(ErrorValue.class)).extracting(ErrorValue::message).asString()
                    .containsIgnoringCase("zero");
        }

        @Test
        @DisplayName("unrolled empty-array path matches the standard in path for an erroring needle")
        void whenErroringNeedleInEmptyArrayThenUnrolledMatchesStandardPath() {
            val unrolled = compileExpression("(10/0) in []", unrollingContext());
            val baseline = compileExpression("(10/0) in []", new CompilationContext(FUNCTION_BROKER));

            assertThat(unrolled).isInstanceOf(ErrorValue.class);
            assertThat(baseline).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    @DisplayName("runtime (non-constant) haystack elements")
    class RuntimeHaystackElements {

        @Test
        @DisplayName("needle in array with an erroring runtime element matches the standard in path")
        void whenHaystackHasErroringRuntimeElementThenUnrolledMatchesStandardPath() {
            // (10/subject.x) is a subscription-dependent element that errors at
            // runtime when subject.x is 0. Unrolling such a haystack diverges
            // from standard IN error propagation, so it must not unroll.
            val expression  = "\"a\" in [\"a\", (10/subject.x)]";
            val unrolledAst = parseExpression(expression);
            val baselineAst = parseExpression(expression);
            val unrolled    = ExpressionCompiler.compile(unrolledAst, unrollingContext());
            val baseline    = ExpressionCompiler.compile(baselineAst, new CompilationContext(FUNCTION_BROKER));

            val subject        = ObjectValue.builder().put("x", Value.of(0)).build();
            val subscription   = AuthorizationSubscription.of(subject, Value.NULL, Value.NULL, Value.NULL);
            val ctx            = evaluationContext(subscription);
            val unrolledResult = ((PureOperator) unrolled).evaluate(ctx);
            val baselineResult = ((PureOperator) baseline).evaluate(ctx);

            assertThat(unrolledResult).isEqualTo(baselineResult);
        }
    }
}
