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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.PdpData;
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
            val variables = Value.ofObject(Map.of("x", Value.of(0)));
            val data      = new PdpData(variables, Value.EMPTY_OBJECT);
            val ctx       = unrollingContext(new CompilationContext(data, FUNCTION_BROKER));

            val ast      = parseExpression("(10/x) in []");
            val compiled = ExpressionCompiler.compile(ast, ctx);
            val result   = evaluate(compiled);

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("unrolled empty-array path matches the standard in path for an erroring needle")
        void whenErroringNeedleInEmptyArrayThenUnrolledMatchesStandardPath() {
            val unrolled = compileExpression("(10/0) in []", unrollingContext());
            val baseline = compileExpression("(10/0) in []", new CompilationContext(FUNCTION_BROKER));

            assertThat(unrolled).isInstanceOf(ErrorValue.class);
            assertThat(baseline).isInstanceOf(ErrorValue.class);
        }

        private Value evaluate(CompiledExpression compiled) {
            return switch (compiled) {
            case Value v         -> v;
            case PureOperator op -> op.evaluate(evaluationContext());
            default              -> Value.errorAt(null, "unexpected compiled shape: %s", compiled);
            };
        }
    }
}
