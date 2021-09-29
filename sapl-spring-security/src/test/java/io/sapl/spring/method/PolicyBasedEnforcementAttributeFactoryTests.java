package io.sapl.spring.method;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.spring.method.attributes.PolicyBasedEnforcementAttributeFactory;

class PolicyBasedEnforcementAttributeFactoryTests {

	@Test
	void whenNoPriorInteraction_thenParserIsNotLoadedButLazyLoadingOnAccess() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		verify(handler, times(0)).getExpressionParser();
		sut.createPostEnforceAttribute("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'", "workersHolder.salaryByWorkers['John']",
				null);
		verify(handler, times(1)).getExpressionParser();
	}

	@Test
	void whenErrorInExpressionPost_thenThrowsIllegalArgumentsException() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenThrow(new ParseException(0, "BAD PARSING"));
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		assertThrows(IllegalArgumentException.class, () -> sut.createPostEnforceAttribute("19 + 1", "1 ne 1",
				"2 > 1 ? 'a' : 'b'", "workersHolder.salaryByWorkers['John']", null));
	}

	@Test
	void whenErrorInExpressionPre_thenThrowsIllegalArgumentsException() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenThrow(new ParseException(0, "BAD PARSING"));
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		assertThrows(IllegalArgumentException.class, () -> sut.createPreEnforceAttribute("19 + 1", "1 ne 1",
				"2 > 1 ? 'a' : 'b'", "workersHolder.salaryByWorkers['John']", null));
	}

	@Test
	void whenPreFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPreEnforceAttribute("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenPostFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPostEnforceAttribute("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenPreFactoryWithEmptyExpression_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPreEnforceAttribute("", "", "", "", null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPostFactoryWithEmptyExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPostEnforceAttribute("", "", "", "", null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPreFactoryWithNullExpression_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPreEnforceAttribute(null, null, null, null, null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPostFactoryWithNullExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new PolicyBasedEnforcementAttributeFactory(handler);
		var attribute = sut.createPostEnforceAttribute(null, null, null, null, null);
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

}
