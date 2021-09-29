package io.sapl.spring.method.pre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

import io.sapl.spring.method.attributes.PreEnforceAttribute;

class PreEnforceAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PreEnforceAttribute(mock(Expression.class), mock(Expression.class), mock(Expression.class),
				mock(Expression.class), Object.class)).isNotNull();
	}

	@Test
	void whenConstructorWithStrings_thenCallSupperSuccessfully() {
		assertThat(new PreEnforceAttribute((String) null, null, null, null, Object.class)).isNotNull();
	}
}
