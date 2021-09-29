package io.sapl.spring.method;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.spring.method.annotations.PostEnforce;
import io.sapl.spring.method.annotations.PreEnforce;
import io.sapl.spring.method.attributes.PolicyBasedEnforcementAttributeFactory;
import io.sapl.spring.method.attributes.PostEnforceAttribute;
import io.sapl.spring.method.attributes.PreEnforceAttribute;
import io.sapl.spring.method.attributes.SaplEnforcementAttributeFactory;
import io.sapl.spring.method.attributes.SaplMethodSecurityMetadataSource;

//@SuppressWarnings("unused")
class SaplMethodSecurityMetadataSourceTests {

	private SaplEnforcementAttributeFactory attributeFactory;

	@BeforeEach
	void beforeEach() {
		var parser = new SpelExpressionParser();
		var handler = mock(MethodSecurityExpressionHandler.class);
		when(handler.getExpressionParser()).thenReturn(parser);
		attributeFactory = new PolicyBasedEnforcementAttributeFactory(handler);
	}

	@Test
	void whenEver_ThenGetAllConfigAttributesIsNull() {
		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		assertThat(sut.getAllConfigAttributes(), is(nullValue()));
	}

	@Test
	void whenInspectedISObjectr_ThenReturnsEmptycollection() throws NoSuchMethodException, SecurityException {
		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = Object.class.getMethod("toString");
		assertThat(sut.getAttributes(method, Object.class), is(empty()));
	}

	@Test
	void whenInspectedHasNotAnnotationsAnywhere_ThenReturnsEmptycollection()
			throws NoSuchMethodException, SecurityException {
		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = NoAnnotations.class.getMethod("doSomething");
		assertThat(sut.getAttributes(method, NoAnnotations.class), is(empty()));
	}

	protected static class NoAnnotations {
		public void doSomething() {
		}
	}

	@Test
	void whenAnnotationOnClassOnly_ThenReturnsAnnotationFromClass() throws NoSuchMethodException, SecurityException {

		@PreEnforce(subject = "'onClass'")
		class TestClass {
			@SuppressWarnings("unused")
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onClass'"))))));
	}

	@Test
	void whenAnnotationOnMethodOnly_ThenReturnsThat() throws NoSuchMethodException, SecurityException {

		class TestClass {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onMethod'"))))));

	}

	@Test
	void whenAnnotationOnMethodAndClass_ThenReturnsOnMetod() throws NoSuchMethodException, SecurityException {

		@PreEnforce(subject = "'onClass'")
		class TestClass {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onMethod'"))))));

	}

	interface TestInterfaceAnnotatedOnMethod {
		@PreEnforce(subject = "'onInterfaceMethod'")
		void doSomething();
	}

	@Test
	void whenAnnotationOnlyOnInterfaceMethod_ThenReturnsThat() throws NoSuchMethodException, SecurityException {

		class TestClass implements TestInterfaceAnnotatedOnMethod {
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onInterfaceMethod'"))))));

	}

	@PreEnforce(subject = "'onInterface'")
	interface TestInterfaceAnnotatedOnInterface {
		void doSomething();
	}

	@Test
	void whenAnnotationOnlyOnInterface_ThenReturnsThat() throws NoSuchMethodException, SecurityException {

		class TestClass implements TestInterfaceAnnotatedOnInterface {
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onInterface'"))))));
	}

	@PreEnforce(subject = "'onInterface'")
	interface TestInterfaceAnnotatedOnInterfaceAndMethod {
		@PreEnforce(subject = "'onInterfaceMethod'")
		void doSomething();
	}

	@Test
	void whenAnnotationOnInterfaceAnInterfaceMethod_ThenReturnsOnInterfaceMethod()
			throws NoSuchMethodException, SecurityException {

		class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onInterfaceMethod'"))))));
	}

	@Test
	void whenAnnotationOnMethodAndClassAndOnInterfaceAndInterfaceMethod_ThenReturnsOnMetod()
			throws NoSuchMethodException, SecurityException {

		@PreEnforce(subject = "'onClass'")
		class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PreEnforceAttribute) attributes.get(0),
				is(pojo(PreEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onMethod'"))))));
	}

	@Test
	void whenAnnotationOnMethod_ThenReturnsOnMetodForPost() throws NoSuchMethodException, SecurityException {

		class TestClass {
			@PostEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PostEnforceAttribute) attributes.get(0),
				is(pojo(PostEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onMethod'"))))));
	}

	interface DefaultMethodInterface {
		@PostEnforce(subject = "'onDefaultInterfaceMethod'")
		default void doSomething() {

		};
	}

	@Test
	void whenAnnotationOnDefaultMethodInInterface_ThenReturnsThat() throws NoSuchMethodException, SecurityException {
		class TestClass implements DefaultMethodInterface {
		}

		var sut = new SaplMethodSecurityMetadataSource(attributeFactory);
		var method = TestClass.class.getMethod("doSomething");
		List<ConfigAttribute> attributes = new ArrayList<>(sut.getAttributes(method, TestClass.class));
		assertThat((PostEnforceAttribute) attributes.get(0),
				is(pojo(PostEnforceAttribute.class).where("getSubjectExpression",
						is(pojo(Expression.class).where("getExpressionString", is("'onDefaultInterfaceMethod'"))))));
	}

}
