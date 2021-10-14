package io.sapl.spring.method.metadata;

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

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

class SaplAttributeFactoryTests {

	@Test
	void whenNoPriorInteraction_thenParserIsNotLoadedButLazyLoadingOnAccess() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);

		when(handler.getExpressionParser()).thenReturn(parser);
		var sut = new SaplAttributeFactory(handler);
		verify(handler, times(0)).getExpressionParser();
		sut.attributeFrom(preEnforceFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		verify(handler, times(1)).getExpressionParser();
	}

	@Test
	void whenErrorInExpressionPost_thenThrowsIllegalArgumentsException() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenThrow(new ParseException(0, "BAD PARSING"));
		var sut = new SaplAttributeFactory(handler);
		assertThrows(IllegalArgumentException.class, () -> sut.attributeFrom(postEnforceFrom("19 + 1", "1 ne 1",
				"2 > 1 ? 'a' : 'b'", "workersHolder.salaryByWorkers['John']", Integer.class)));
	}

	@Test
	void whenErrorInExpressionPre_thenThrowsIllegalArgumentsException() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenThrow(new ParseException(0, "BAD PARSING"));
		var sut = new SaplAttributeFactory(handler);
		assertThrows(IllegalArgumentException.class, () -> sut.attributeFrom(preEnforceFrom("19 + 1", "1 ne 1",
				"2 > 1 ? 'a' : 'b'", "workersHolder.salaryByWorkers['John']", Integer.class)));
	}

	@Test
	void whenPreFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(preEnforceFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getGenericsType(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenPostFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(postEnforceFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getGenericsType(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenEnforceTillDenyFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceTillDeniedFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getGenericsType(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenEnforceDropWhileDeniedFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceDropWhileDeniedFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getGenericsType(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenEnforceRecoverableIfDeniedFactoryWithExpressions_thenAllOfThemAreSet() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var parser = mock(ExpressionParser.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		when(parser.parseExpression(any())).thenReturn(mock(Expression.class));
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceRecoverableIfDeniedFrom("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
				"workersHolder.salaryByWorkers['John']", Integer.class));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), notNullValue()),
				() -> assertThat(attribute.getActionExpression(), notNullValue()),
				() -> assertThat(attribute.getResourceExpression(), notNullValue()),
				() -> assertThat(attribute.getGenericsType(), notNullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), notNullValue()));
	}

	@Test
	void whenPreFactoryWithEmptyExpression_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(preEnforceFrom("", "", "", "", null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPostFactoryWithEmptyExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(postEnforceFrom("", "", "", "", null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceTillDeniedFactoryWithEmptyExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceTillDeniedFrom("", "", "", "", null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceDropWhileDeniedFactoryWithEmptyExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceDropWhileDeniedFrom("", "", "", "", null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceRecoverableIfDeniedFactoryWithEmptyExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceRecoverableIfDeniedFrom("", "", "", "", null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPreFactoryWithNullExpression_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(preEnforceFrom(null, null, null, null, null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenPostFactoryWithNullExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(postEnforceFrom(null, null, null, null, null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceTillDeniedWithNullExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceTillDeniedFrom(null, null, null, null, null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceDropWhileDeniedFactoryWithNullExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceDropWhileDeniedFrom(null, null, null, null, null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	@Test
	void whenEnforceRecoverableIfDeniedFactoryWithNullExpressions_thenAllOfThemAreNull() {
		var handler = mock(MethodSecurityExpressionHandler.class);
		var sut = new SaplAttributeFactory(handler);
		var attribute = sut.attributeFrom(enforceRecoverableIfDeniedFrom(null, null, null, null, null));
		assertAll(() -> assertThat(attribute.getSubjectExpression(), nullValue()),
				() -> assertThat(attribute.getActionExpression(), nullValue()),
				() -> assertThat(attribute.getResourceExpression(), nullValue()),
				() -> assertThat(attribute.getGenericsType(), nullValue()),
				() -> assertThat(attribute.getEnvironmentExpression(), nullValue()));
	}

	private PreEnforce preEnforceFrom(String subject, String action, String resource, String environment,
			Class<?> genericsType) {
		return new PreEnforce() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return PreEnforce.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}

	private PostEnforce postEnforceFrom(String subject, String action, String resource, String environment,
			Class<?> genericsType) {
		return new PostEnforce() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return PostEnforce.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}

	private EnforceTillDenied enforceTillDeniedFrom(String subject, String action, String resource, String environment,
			Class<?> genericsType) {
		return new EnforceTillDenied() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return EnforceTillDenied.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}

	private EnforceDropWhileDenied enforceDropWhileDeniedFrom(String subject, String action, String resource,
			String environment, Class<?> genericsType) {
		return new EnforceDropWhileDenied() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return EnforceDropWhileDenied.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}

	private EnforceRecoverableIfDenied enforceRecoverableIfDeniedFrom(String subject, String action, String resource,
			String environment, Class<?> genericsType) {
		return new EnforceRecoverableIfDenied() {
			@Override
			public Class<? extends Annotation> annotationType() {
				return EnforceRecoverableIfDenied.class;
			}

			@Override
			public String subject() {
				return subject;
			}

			@Override
			public String action() {
				return action;
			}

			@Override
			public String resource() {
				return resource;
			}

			@Override
			public String environment() {
				return environment;
			}

			@Override
			public Class<?> genericsType() {
				return genericsType;
			}
		};
	}
}
