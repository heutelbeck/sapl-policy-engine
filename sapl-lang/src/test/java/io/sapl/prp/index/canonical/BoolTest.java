/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import reactor.core.publisher.Flux;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BoolTest {

	Bool constantBool;

	Bool expressionBool;

	@BeforeEach
	void setUp() {
		constantBool = new Bool(false);

		var expressionMock = mock(Expression.class, RETURNS_DEEP_STUBS);
		when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(Val.TRUE));
		expressionBool = new Bool(expressionMock, Collections.emptyMap());
	}

	@Test
	void constructUsingConstantTest() {
		assertThat(constantBool, is(notNullValue()));
		assertThat(constantBool.isImmutable(), is(true));
		assertThat(constantBool.hashCode(), not(is(0)));
	}

	@Test
	void constructUsingExpressionTest() {
		assertThat(expressionBool, is(notNullValue()));
		assertThat(expressionBool.isImmutable(), is(false));
	}

	@Test
	@SuppressWarnings("unlikely-arg-type")
	void equalsTest() {
		assertThat(constantBool.equals(constantBool), is(true));
		assertThat(expressionBool.equals(expressionBool), is(true));
		assertThat(expressionBool.equals(constantBool), is(false));

		assertThat(constantBool.equals(null), is(false));
		assertThat(constantBool.equals(""), is(false));

		assertThat(constantBool.equals(new Bool(false)), is(true));
		assertThat(constantBool.equals(new Bool(true)), is(false));

		try (MockedStatic<EquivalenceAndHashUtil> mock = mockStatic(EquivalenceAndHashUtil.class)) {
			mock.when(() -> EquivalenceAndHashUtil.semanticHash(any(), any())).thenReturn(42);

			var expressionMock = mock(Expression.class, RETURNS_DEEP_STUBS);
			var otherExpressionBool = new Bool(expressionMock, Collections.emptyMap());

			assertThat(expressionBool.equals(otherExpressionBool), is(false));
		}

	}

	@Test
	void evaluate_immutable_bool() {
		assertThat(constantBool.evaluate(), is(false));
		assertThrows(IllegalStateException.class, () -> expressionBool.evaluate());
	}

	@Test
	void evaluating_bool_with_error_expression_should_return_error() {
		var contextMock = mock(EvaluationContext.class);
		when(contextMock.withImports(any())).thenReturn(contextMock);

		var expressionMock = mock(Expression.class);
		when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(Val.error("error")));

		var bool = new Bool(expressionMock, Collections.emptyMap());
		var result = bool.evaluate(contextMock).block();

		assertThat(result.isError(), is(true));
		assertThat(result.getMessage(), is("error"));
	}

	@Test
	void evaluating_bool_with_false_expression_should_return_false() {
		var contextMock = mock(EvaluationContext.class);
		when(contextMock.withImports(any())).thenReturn(contextMock);

		var expressionMock = mock(Expression.class);
		when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(Val.FALSE));

		var bool = new Bool(expressionMock, Collections.emptyMap());
		var result = bool.evaluate(contextMock).block();

		assertThat(result.isBoolean(), is(true));
		assertThat(result.getBoolean(), is(false));
	}

	@Test
	void evaluating_bool_with_long_expression_should_return_error() {
		var contextMock = mock(EvaluationContext.class);
		when(contextMock.withImports(any())).thenReturn(contextMock);

		var expressionMock = mock(Expression.class);
		when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(Val.of(0L)));

		var bool = new Bool(expressionMock, Collections.emptyMap());
		var result = bool.evaluate(contextMock).block();

		assertThat(result.isBoolean(), is(false));
		assertThat(result.isError(), is(true));
	}

	@Test
	void evaluating_bool_with_impossible_expression_should_return_impossible_value() {
		var contextMock = mock(EvaluationContext.class);
		when(contextMock.withImports(any())).thenReturn(contextMock);

		// condition coverage requires Val to be boolean and error at the same time ->
		// impossible
		var valMock = mock(Val.class);
		when(valMock.isError()).thenReturn(true);
		when(valMock.isBoolean()).thenReturn(true);

		var expressionMock = mock(Expression.class);
		when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(valMock));

		var bool = new Bool(expressionMock, Collections.emptyMap());
		var result = bool.evaluate(contextMock).block();

		assertThat(result.isError(), is(true));
		assertThat(result.isBoolean(), is(true));
	}

}
