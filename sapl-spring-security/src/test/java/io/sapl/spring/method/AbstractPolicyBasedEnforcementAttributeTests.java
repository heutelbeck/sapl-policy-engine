package io.sapl.spring.method;

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

import io.sapl.spring.method.attributes.AbstractPolicyBasedEnforcementAttribute;

class AbstractPolicyBasedEnforcementAttributeTests {

	@Test
	void whenCalled_thenGetAtributeAlwaysNull() {
		var sut = mock(AbstractPolicyBasedEnforcementAttribute.class, Mockito.CALLS_REAL_METHODS);
		assertThat(sut.getAttribute(), is(nullValue()));
	}

	@Test
	void whenToStringCalled_thenStringContainsTheThreeKeywords() {
		var sut = mock(AbstractPolicyBasedEnforcementAttribute.class, Mockito.CALLS_REAL_METHODS);
		var stringValue = sut.toString();
		assertAll(() -> assertThat(stringValue, containsString("subject")),
				() -> assertThat(stringValue, containsString("action")),
				() -> assertThat(stringValue, containsString("resource")),
				() -> assertThat(stringValue, containsString("environment")));
	}

	@Test
	void whenPassingStrings_thenExpressionsAreParsedAndSet() {

	}

	@Test
	void whenPassingNull_thenExpressionsAreNull() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock((String) null, null, null, null, null);
		assertAll(() -> assertThat(sut.getSubjectExpression(), is(nullValue())),
				() -> assertThat(sut.getActionExpression(), is(nullValue())),
				() -> assertThat(sut.getResourceExpression(), is(nullValue())),
				() -> assertThat(sut.getEnvironmentExpression(), is(nullValue())));
	}

	@Test
	void whenExpressions_thenExpressionsAreSet() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", null);
		assertAll(() -> assertThat(sut.getSubjectExpression(), is(notNullValue())),
				() -> assertThat(sut.getActionExpression(), is(notNullValue())),
				() -> assertThat(sut.getResourceExpression(), is(notNullValue())),
				() -> assertThat(sut.getEnvironmentExpression(), is(notNullValue())));
	}

	@Test
	void whenExpressionsSet_thenToStringcontainsThem() {
		var sut = new AbstractPolicyBasedEnforcementAttributeMock("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", null);
		var stringValue = sut.toString();
		assertAll(() -> assertThat(stringValue, containsString("19 + 1")),
				() -> assertThat(stringValue, containsString("1 ne 1")),
				() -> assertThat(stringValue, containsString("2 > 1 ? 'a' : 'b'")),
				() -> assertThat(stringValue, containsString("workersHolder.salaryByWorkers['John']")));
	}

	protected static class AbstractPolicyBasedEnforcementAttributeMock extends AbstractPolicyBasedEnforcementAttribute {
		public AbstractPolicyBasedEnforcementAttributeMock(String subjectExpression, String actionExpression,
				String resourceExpression, String environmentExpression, Class<?> genericsType) {
			super(subjectExpression, actionExpression, resourceExpression, environmentExpression, genericsType);
		}

		public AbstractPolicyBasedEnforcementAttributeMock(Expression subjectExpression, Expression actionExpression,
				Expression resourceExpression, Expression environmentExpression, Class<?> genericsType) {
			super(subjectExpression, actionExpression, resourceExpression, environmentExpression, genericsType);
		}
	}
}
