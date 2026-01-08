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

import io.sapl.api.model.*;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.sapl.compiler.AttributeOptionsCompiler.*;
import static io.sapl.util.TestBrokers.DEFAULT_FUNCTION_BROKER;
import static io.sapl.util.TestBrokers.ERROR_ATTRIBUTE_BROKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AttributeOptionsCompilerTests {

    private static final SourceLocation LOCATION = new SourceLocation("test", null, 0, 10, 1, 1);

    private CompilationContext compilationCtx;

    @BeforeEach
    void setUp() {
        compilationCtx = mock(CompilationContext.class);
    }

    @Nested
    class CompileOptionsTests {

        @Test
        void when_nullOptionsExpression_then_returnsOptionsRecord() {
            val result = AttributeOptionsCompiler.compileOptions(null, LOCATION, compilationCtx);

            assertThat(result).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_objectValueWithAllOptions_then_returnsObjectValueDirectly() {
            val allOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(5000L))
                    .put(OPTION_POLL_INTERVAL, Value.of(60000L)).put(OPTION_BACKOFF, Value.of(2000L))
                    .put(OPTION_RETRIES, Value.of(5L)).put(OPTION_FRESH, Value.TRUE).build();
            val expr       = new Literal(allOptions, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(allOptions);

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isSameAs(allOptions);
            }
        }

        @Test
        void when_objectValueWithPartialOptions_then_returnsOptionsRecord() {
            val partialOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(5000L)).build();
            val expr           = new Literal(partialOptions, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(partialOptions);

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isInstanceOf(PureOperator.class);
            }
        }

        @Test
        void when_pureOperator_then_returnsOptionsRecord() {
            val pureOp = mock(PureOperator.class);
            val expr   = new Literal(Value.NULL, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(pureOp);

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isInstanceOf(PureOperator.class);
            }
        }

        @Test
        void when_streamOperator_then_returnsError() {
            val streamOp = mock(StreamOperator.class);
            val expr     = new Literal(Value.NULL, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(streamOp);

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Attribute access not permitted");
            }
        }

        @Test
        void when_nonObjectValue_then_returnsError() {
            val expr = new Literal(Value.NULL, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(Value.of(123));

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("type mismatch");
            }
        }

        @Test
        void when_errorValue_then_propagatesError() {
            val error = Value.error("compilation error");
            val expr  = new Literal(Value.NULL, LOCATION);

            try (var mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, compilationCtx)).thenReturn(error);

                val result = AttributeOptionsCompiler.compileOptions(expr, LOCATION, compilationCtx);

                assertThat(result).isSameAs(error);
            }
        }
    }

    @Nested
    class OptionsEvaluateTests {

        // Helper to create real EvaluationContext with specific attribute finder
        // options
        private EvaluationContext evalContextWith(Value attributeFinderOptions) {
            Map<String, Value> variables = attributeFinderOptions != null
                    ? Map.of(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, attributeFinderOptions)
                    : Map.of();
            return new EvaluationContext(null, null, null, null, variables, DEFAULT_FUNCTION_BROKER,
                    ERROR_ATTRIBUTE_BROKER, () -> "test");
        }

        @Test
        void when_policySettingsTakePriority_then_usesPolicyValues() {
            val policyOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(9999L)).build();
            val pdpOptions    = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(1111L)).build();
            val evalCtx       = evalContextWith(pdpOptions);

            val options = new AttributeOptionsCompiler.Options(policyOptions, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) result;
            assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(9999L));
        }

        @Test
        void when_policyMissingOption_then_fallsBackToPdp() {
            val policyOptions = ObjectValue.builder().put(OPTION_RETRIES, Value.of(10L)).build();
            val pdpOptions    = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(5000L))
                    .put(OPTION_RETRIES, Value.of(1L)).build();
            val evalCtx       = evalContextWith(pdpOptions);

            val options = new AttributeOptionsCompiler.Options(policyOptions, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) result;
            assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(5000L));
            assertThat(obj.get(OPTION_RETRIES)).isEqualTo(Value.of(10L));
        }

        @Test
        void when_noPolicyAndNoPdp_then_returnsEmptyObject() {
            val evalCtx = evalContextWith(null);

            val options = new AttributeOptionsCompiler.Options(null, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) result;
            assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isNull();
        }

        @Test
        void when_pdpSettingsNotObject_then_returnsError() {
            val evalCtx = evalContextWith(Value.of("invalid"));

            val options = new AttributeOptionsCompiler.Options(null, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("type mismatch");
        }

        @Test
        void when_policyIsPureOperator_then_evaluatesIt() {
            val evaluatedOptions = ObjectValue.builder().put(OPTION_FRESH, Value.TRUE).build();
            val pureOp           = mock(PureOperator.class);
            val evalCtx          = evalContextWith(null);
            when(pureOp.evaluate(evalCtx)).thenReturn(evaluatedOptions);

            val options = new AttributeOptionsCompiler.Options(pureOp, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) result;
            assertThat(obj.get(OPTION_FRESH)).isEqualTo(Value.TRUE);
        }

        @Test
        void when_policyPureOperatorReturnsError_then_propagatesError() {
            val error   = Value.error("evaluation failed");
            val pureOp  = mock(PureOperator.class);
            val evalCtx = evalContextWith(null);
            when(pureOp.evaluate(evalCtx)).thenReturn(error);

            val options = new AttributeOptionsCompiler.Options(pureOp, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isSameAs(error);
        }

        @Test
        void when_policyPureOperatorReturnsNonObject_then_returnsError() {
            val pureOp  = mock(PureOperator.class);
            val evalCtx = evalContextWith(null);
            when(pureOp.evaluate(evalCtx)).thenReturn(Value.of(42));

            val options = new AttributeOptionsCompiler.Options(pureOp, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("type mismatch");
        }

        @Test
        void when_allOptionsFromPolicy_then_ignoresPdp() {
            val policyOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(1L))
                    .put(OPTION_POLL_INTERVAL, Value.of(2L)).put(OPTION_BACKOFF, Value.of(3L))
                    .put(OPTION_RETRIES, Value.of(4L)).put(OPTION_FRESH, Value.TRUE).build();
            val pdpOptions    = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(9999L))
                    .put(OPTION_POLL_INTERVAL, Value.of(9999L)).put(OPTION_BACKOFF, Value.of(9999L))
                    .put(OPTION_RETRIES, Value.of(9999L)).put(OPTION_FRESH, Value.FALSE).build();
            val evalCtx       = evalContextWith(pdpOptions);

            val options = new AttributeOptionsCompiler.Options(policyOptions, LOCATION);
            val result  = options.evaluate(evalCtx);

            assertThat(result).isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) result;
            assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(1L));
            assertThat(obj.get(OPTION_POLL_INTERVAL)).isEqualTo(Value.of(2L));
            assertThat(obj.get(OPTION_BACKOFF)).isEqualTo(Value.of(3L));
            assertThat(obj.get(OPTION_RETRIES)).isEqualTo(Value.of(4L));
            assertThat(obj.get(OPTION_FRESH)).isEqualTo(Value.TRUE);
        }

        @Test
        void when_isDependingOnSubscription_then_returnsTrue() {
            val options = new AttributeOptionsCompiler.Options(null, LOCATION);

            assertThat(options.isDependingOnSubscription()).isTrue();
        }
    }
}
