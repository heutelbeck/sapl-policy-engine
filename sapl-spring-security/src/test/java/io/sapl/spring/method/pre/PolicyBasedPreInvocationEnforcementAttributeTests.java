package io.sapl.spring.method.pre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class PolicyBasedPreInvocationEnforcementAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PolicyBasedPreInvocationEnforcementAttribute(mock(Expression.class), mock(Expression.class),
				mock(Expression.class), mock(Expression.class))).isNotNull();
	}

	@Test
	void whenConstructorWithStrings_thenCallSupperSuccessfully() {
		assertThat(new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null)).isNotNull();
	}
}
