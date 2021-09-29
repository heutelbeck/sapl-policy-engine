package io.sapl.spring.method.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

import io.sapl.spring.method.attributes.PostEnforceAttribute;

class PostEnforceAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PostEnforceAttribute(mock(Expression.class), mock(Expression.class), mock(Expression.class),
				mock(Expression.class), Object.class)).isNotNull();
	}

	@Test
	void whenConstructorWithStrings_thenCallSupperSuccessfully() {
		assertThat(new PostEnforceAttribute((String) null, null, null, null, Object.class)).isNotNull();
	}
}
