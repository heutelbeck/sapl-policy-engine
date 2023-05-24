/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.method.metadata;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasValue;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationConfigurationException;
import org.springframework.expression.Expression;
import org.springframework.security.util.MethodInvocationUtils;

class SaplAttributeRegistryTests {

	@Test
	void whenInspectedIsObject_ThenReturnsEmptyCollection() throws NoSuchMethodException {
		var sut = new SaplAttributeRegistry();
		var mi  = MethodInvocationUtils.createFromClass(Object.class, "toString");
		assertThat(sut.getAllSaplAttributes(mi), anEmptyMap());
	}

	@Test
	void whenInspectedHasNotAnnotationsAnywhere_ThenReturnsEmptyCollection() throws NoSuchMethodException {

		class NoAnnotations {
			@SuppressWarnings("unused")
			public void doSomething() {
			}
		}

		var sut = new SaplAttributeRegistry();
		var mi  = MethodInvocationUtils.createFromClass(NoAnnotations.class, "doSomething");
		assertThat(sut.getAllSaplAttributes(mi), anEmptyMap());
	}

	@Test
	void whenAnnotationOnClassOnly_ThenReturnsAnnotationFromClass() throws NoSuchMethodException {

		@PreEnforce(subject = "'onClass'")
		class TestClass {
			@SuppressWarnings("unused")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onClass'");
	}

	@Test
	void whenAnnotationOnMethodOnly_ThenReturnsThat() throws NoSuchMethodException {

		class TestClass {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	@Test
	void whenAnnotationOnMethodAndClass_ThenReturnsOnMethod() throws NoSuchMethodException {

		@PreEnforce(subject = "'onClass'")
		class TestClass {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	interface TestInterfaceAnnotatedOnMethod {

		@PreEnforce(subject = "'onInterfaceMethod'")
		void doSomething();

	}

	@Test
	void whenAnnotationOnlyOnInterfaceMethod_ThenReturnsThat() throws NoSuchMethodException {

		class TestClass implements TestInterfaceAnnotatedOnMethod {
			public void doSomething() {
			}
		}
		expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterfaceMethod'");
	}

	@PreEnforce(subject = "'onInterface'")
	interface TestInterfaceAnnotatedOnInterface {
		void doSomething();
	}

	@Test
	void whenAnnotationOnlyOnInterface_ThenReturnsThat() throws NoSuchMethodException {

		class TestClass implements TestInterfaceAnnotatedOnInterface {
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterface'");
	}

	@PreEnforce(subject = "'onInterface'")
	interface TestInterfaceAnnotatedOnInterfaceAndMethod {
		@PreEnforce(subject = "'onInterfaceMethod'")
		void doSomething();
	}

	@Test
	void whenAnnotationOnInterfaceAnInterfaceMethod_ThenReturnsOnInterfaceMethod() throws NoSuchMethodException {

		class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterfaceMethod'");
	}

	@Test
	void whenAnnotationOnMethodAndClassAndOnInterfaceAndInterfaceMethod_ThenReturnsOnMethod()
			throws NoSuchMethodException {

		@PreEnforce(subject = "'onClass'")
		class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
			@PreEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		var sut = new SaplAttributeRegistry();
		var mi  = MethodInvocationUtils.createFromClass(TestClass.class, "doSomething");
		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	@Test
	void whenAnnotationOnMethod_ThenReturnsOnMethodForPost() throws NoSuchMethodException {

		class TestClass {
			@PostEnforce(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	@Test
	void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceTillDenied() throws NoSuchMethodException {

		class TestClass {
			@EnforceTillDenied(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	@Test
	void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceDropWhileDenied() throws NoSuchMethodException {

		class TestClass {
			@EnforceDropWhileDenied(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	@Test
	void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceRecoverableIfDenied() throws NoSuchMethodException {

		class TestClass {
			@EnforceRecoverableIfDenied(subject = "'onMethod'")
			public void doSomething() {
			}
		}

		expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
	}

	interface DefaultMethodInterface {
		@PostEnforce(subject = "'onDefaultInterfaceMethod'")
		default void doSomething() {
		}
	}

	@Test
	void whenAnnotationOnDefaultMethodInInterface_ThenReturnsThat() throws NoSuchMethodException {
		class TestClass implements DefaultMethodInterface {
		}
		expectSubjectExpressionStringInAttribute(TestClass.class, "'onDefaultInterfaceMethod'");
	}

	private void expectSubjectExpressionStringInAttribute(Class<?> clazz,
			String expectedExpressionString) {
		var sut        = new SaplAttributeRegistry();
		var mi         = MethodInvocationUtils.createFromClass(clazz, "doSomething");
		var attributes = sut.getAllSaplAttributes(mi);
		assertThat(attributes, hasValue(
				is(pojo(SaplAttribute.class)
						.where("subjectExpression",
								is(pojo(Expression.class)
										.where("getExpressionString",
												is(expectedExpressionString)))))));
	}

}
