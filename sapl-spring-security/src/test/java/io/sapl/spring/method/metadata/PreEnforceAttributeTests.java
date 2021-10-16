package io.sapl.spring.method.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

class PreEnforceAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PreEnforceAttribute(mock(Expression.class), mock(Expression.class), mock(Expression.class),
				mock(Expression.class), Object.class)).isNotNull();
	}

}