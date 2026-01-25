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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.ast.Expression;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static io.sapl.compiler.expressions.AttributeOptionsCompiler.DEFAULT_BACKOFF_MS;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.DEFAULT_POLL_INTERVAL_MS;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.DEFAULT_RETRIES;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.DEFAULT_TIMEOUT_MS;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_BACKOFF;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_FRESH;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_INITIAL_TIMEOUT;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_POLL_INTERVAL;
import static io.sapl.compiler.expressions.AttributeOptionsCompiler.OPTION_RETRIES;
import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static io.sapl.util.SaplTesting.TestPureOperator;
import static io.sapl.util.SaplTesting.TestStreamOperator;
import static io.sapl.util.SaplTesting.compilationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mockStatic;

@DisplayName("AttributeOptionsCompiler")
class AttributeOptionsCompilerTests {

    @Nested
    @DisplayName("when null options expression")
    class WhenNullOptionsExpression {

        @Test
        @DisplayName("with no PDP settings returns all defaults")
        void withNoPdpSettings_returnsDefaultSettings() {
            val ctx    = compilationContext();
            val result = AttributeOptionsCompiler.compileOptions(null, ctx);

            assertThat(result).isInstanceOf(ObjectValue.class).satisfies(r -> {
                val obj = (ObjectValue) r;
                assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(DEFAULT_TIMEOUT_MS));
                assertThat(obj.get(OPTION_POLL_INTERVAL)).isEqualTo(Value.of(DEFAULT_POLL_INTERVAL_MS));
                assertThat(obj.get(OPTION_BACKOFF)).isEqualTo(Value.of(DEFAULT_BACKOFF_MS));
                assertThat(obj.get(OPTION_RETRIES)).isEqualTo(Value.of(DEFAULT_RETRIES));
                assertThat(obj.get(OPTION_FRESH)).isEqualTo(Value.FALSE);
            });
        }

        @Test
        @DisplayName("with PDP settings merges them into defaults")
        void withPdpSettings_mergesPdpIntoDefaults() {
            val pdpOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(9999L))
                    .put(OPTION_RETRIES, Value.of(10L)).build();
            val variables  = ObjectValue.builder().put(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, pdpOptions).build();
            val ctx        = compilationContext(variables);
            val result     = AttributeOptionsCompiler.compileOptions(null, ctx);

            assertThat(result).isInstanceOf(ObjectValue.class).satisfies(r -> {
                val obj = (ObjectValue) r;
                assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(9999L));
                assertThat(obj.get(OPTION_RETRIES)).isEqualTo(Value.of(10L));
                assertThat(obj.get(OPTION_POLL_INTERVAL)).isEqualTo(Value.of(DEFAULT_POLL_INTERVAL_MS));
            });
        }

        @Test
        @DisplayName("with invalid PDP settings throws exception")
        void withInvalidPdpSettings_throwsException() {
            val invalidPdpOptions = Value.of("not an object");
            val variables         = ObjectValue.builder().put(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, invalidPdpOptions)
                    .build();
            val ctx               = compilationContext(variables);
            val expr              = new Literal(Value.NULL, TEST_LOCATION);

            assertThatThrownBy(() -> AttributeOptionsCompiler.compileOptions(expr, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("PDP wide defaults");
        }
    }

    @Nested
    @DisplayName("when options expression provided")
    class WhenOptionsExpressionProvided {

        @Test
        @DisplayName("with all options returns merged ObjectValue")
        void withAllOptions_returnsMergedObjectValue() {
            val allOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(5000L))
                    .put(OPTION_POLL_INTERVAL, Value.of(60000L)).put(OPTION_BACKOFF, Value.of(2000L))
                    .put(OPTION_RETRIES, Value.of(5L)).put(OPTION_FRESH, Value.TRUE).build();
            val expr       = new Literal(allOptions, TEST_LOCATION);
            val ctx        = compilationContext();

            val result = compileWithMockedExpression(expr, ctx, allOptions);

            assertThat(result).isInstanceOf(ObjectValue.class).satisfies(r -> {
                val obj = (ObjectValue) r;
                assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(5000L));
                assertThat(obj.get(OPTION_FRESH)).isEqualTo(Value.TRUE);
            });
        }

        @Test
        @DisplayName("with partial options merges with defaults")
        void withPartialOptions_mergesWithDefaults() {
            val partialOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(5000L)).build();
            val expr           = new Literal(partialOptions, TEST_LOCATION);
            val ctx            = compilationContext();

            val result = compileWithMockedExpression(expr, ctx, partialOptions);

            assertThat(result).isInstanceOf(ObjectValue.class).satisfies(r -> {
                val obj = (ObjectValue) r;
                assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(5000L));
                assertThat(obj.get(OPTION_POLL_INTERVAL)).isEqualTo(Value.of(DEFAULT_POLL_INTERVAL_MS));
            });
        }

        @Test
        @DisplayName("policy options take priority over PDP options")
        void withPolicyAndPdpOptions_policyTakesPriority() {
            val policyOptions = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(1111L)).build();
            val pdpOptions    = ObjectValue.builder().put(OPTION_INITIAL_TIMEOUT, Value.of(9999L))
                    .put(OPTION_RETRIES, Value.of(10L)).build();
            val variables     = ObjectValue.builder().put(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS, pdpOptions).build();
            val expr          = new Literal(policyOptions, TEST_LOCATION);
            val ctx           = compilationContext(variables);

            val result = compileWithMockedExpression(expr, ctx, policyOptions);

            assertThat(result).isInstanceOf(ObjectValue.class).satisfies(r -> {
                val obj = (ObjectValue) r;
                assertThat(obj.get(OPTION_INITIAL_TIMEOUT)).isEqualTo(Value.of(1111L));
                assertThat(obj.get(OPTION_RETRIES)).isEqualTo(Value.of(10L));
            });
        }
    }

    @Nested
    @DisplayName("when expression compiles to invalid type")
    class WhenExpressionCompilesToInvalidType {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("throws exception")
        void throwsException(String description, CompiledExpression compiled, String expectedMessage) {
            val expr = new Literal(Value.NULL, TEST_LOCATION);
            val ctx  = compilationContext();

            try (MockedStatic<ExpressionCompiler> mockedCompiler = mockStatic(ExpressionCompiler.class)) {
                mockedCompiler.when(() -> ExpressionCompiler.compile(expr, ctx)).thenReturn(compiled);

                assertThatThrownBy(() -> AttributeOptionsCompiler.compileOptions(expr, ctx))
                        .isInstanceOf(SaplCompilerException.class).hasMessageContaining(expectedMessage);
            }
        }

        static Stream<Arguments> throwsException() {
            return Stream.of(
                    arguments("StreamOperator", new TestStreamOperator(Value.NULL), "Attribute access not permitted"),
                    arguments("PureOperator", new TestPureOperator(evalCtx -> Value.NULL),
                            "must not depend on any element of the authorization subscription"),
                    arguments("non-object Value", Value.of(123), "must be an object"));
        }
    }

    private static CompiledExpression compileWithMockedExpression(Expression expr, CompilationContext ctx,
            CompiledExpression returnValue) {
        try (MockedStatic<ExpressionCompiler> mockedCompiler = mockStatic(ExpressionCompiler.class)) {
            mockedCompiler.when(() -> ExpressionCompiler.compile(expr, ctx)).thenReturn(returnValue);
            return AttributeOptionsCompiler.compileOptions(expr, ctx);
        }
    }
}
