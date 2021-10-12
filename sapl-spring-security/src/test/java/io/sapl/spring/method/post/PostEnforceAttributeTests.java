package io.sapl.spring.method.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;

import io.sapl.spring.method.metadata.PostEnforceAttribute;

class PostEnforceAttributeTests {

	@Test
	void whenConstructorWithExpressions_thenCallSupperSuccessfully() {
		assertThat(new PostEnforceAttribute(mock(Expression.class), mock(Expression.class), mock(Expression.class),
				mock(Expression.class), Object.class)).isNotNull();
	}

}
