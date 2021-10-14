package io.sapl.spring.method.metadata;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class AbstractSaplAttributeTests {

	@Test
	void whenCalled_thenGetAtributeAlwaysNull() {
		var sut = mock(AbstractSaplAttribute.class, Mockito.CALLS_REAL_METHODS);
		assertThat(sut.getAttribute(), is(nullValue()));
	}

	@Test
	void whenToStringCalled_thenStringContainsTheKeywords() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock(null, null, null, null, null);
		var stringValue = sut.toString();
		assertAll(() -> assertThat(stringValue, containsString("subject")),
				() -> assertThat(stringValue, containsString("action")),
				() -> assertThat(stringValue, containsString("resource")),
				() -> assertThat(stringValue, containsString("genericsType")),
				() -> assertThat(stringValue, containsString("environment")));
	}

	@Test
	void whenPassingNonNull_thenStringContainsTheKeywords() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock(toExpression("19 + 1"), toExpression("1 ne 1"),
				toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"),
				Integer.class);
		var stringValue = sut.toString();
		assertAll(() -> assertThat(stringValue, containsString("subject")),
				() -> assertThat(stringValue, containsString("action")),
				() -> assertThat(stringValue, containsString("resource")),
				() -> assertThat(stringValue, containsString("genericsType")),
				() -> assertThat(stringValue, containsString("environment")));
	}

	@Test
	void whenPassingNull_thenExpressionsAreNull() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock(null, null, null, null, null);
		assertAll(() -> assertThat(sut.getSubjectExpression(), is(nullValue())),
				() -> assertThat(sut.getActionExpression(), is(nullValue())),
				() -> assertThat(sut.getResourceExpression(), is(nullValue())),
				() -> assertThat(sut.getGenericsType(), is(nullValue())),
				() -> assertThat(sut.getEnvironmentExpression(), is(nullValue())));
	}

	@Test
	void whenExpressions_thenExpressionsAreSet() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock(toExpression("19 + 1"), toExpression("1 ne 1"),
				toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), null);
		assertAll(() -> assertThat(sut.getSubjectExpression(), is(notNullValue())),
				() -> assertThat(sut.getActionExpression(), is(notNullValue())),
				() -> assertThat(sut.getResourceExpression(), is(notNullValue())),
				() -> assertThat(sut.getEnvironmentExpression(), is(notNullValue())));
	}

	@Test
	void whenExpressionsSet_thenToStringcontainsThem() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock(toExpression("19 + 1"), toExpression("1 ne 1"),
				toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), null);
		var stringValue = sut.toString();
		assertAll(() -> assertThat(stringValue, containsString("19 + 1")),
				() -> assertThat(stringValue, containsString("1 ne 1")),
				() -> assertThat(stringValue, containsString("2 > 1 ? 'a' : 'b'")),
				() -> assertThat(stringValue, containsString("workersHolder.salaryByWorkers['John']")));
	}

	private static Expression toExpression(String expression) {
		return new SpelExpressionParser().parseExpression(expression);
	}

	protected static class AbstractPolicyBasedEnforcementAttributeMock extends AbstractSaplAttribute {

		public AbstractPolicyBasedEnforcementAttributeMock(Expression subjectExpression, Expression actionExpression,
				Expression resourceExpression, Expression environmentExpression, Class<?> genericsType) {
			super(subjectExpression, actionExpression, resourceExpression, environmentExpression, genericsType);
		}

	}
}
