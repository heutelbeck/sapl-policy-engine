package io.sapl.spring.method.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class PolicyBasedPostInvocationEnforcementAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PolicyBasedPostInvocationEnforcementAttribute(mock(Expression.class), mock(Expression.class),
				mock(Expression.class), mock(Expression.class))).isNotNull();
	}

	@Test
	void whenConstructorWithStrings_thenCallSupperSuccessfully() {
		assertThat(new PolicyBasedPostInvocationEnforcementAttribute((String) null, null, null, null)).isNotNull();
	}
}
