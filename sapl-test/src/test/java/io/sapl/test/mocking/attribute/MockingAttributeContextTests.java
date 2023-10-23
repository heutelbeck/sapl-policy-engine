/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.mocking.attribute;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.unit.TestPIP;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class MockingAttributeContextTests {

	private AttributeContext          unmockedCtx;
	private MockingAttributeContext   attrCtx;
	private HashMap<String, JsonNode> variables;

	@BeforeEach
	void setup() {
		this.unmockedCtx = Mockito.mock(AnnotationAttributeContext.class);
		this.attrCtx     = new MockingAttributeContext(unmockedCtx);
		this.variables   = new HashMap<>();
	}

	@Test
	void templatesEmpty() {
		assertThat(this.attrCtx.getAttributeCodeTemplates()).isEmpty();
		assertThat(this.attrCtx.getEnvironmentAttributeCodeTemplates()).isEmpty();
		assertThat(this.attrCtx.getAllFullyQualifiedFunctions()).isEmpty();
	}

	@Test
	void test_dynamicMock() {
		attrCtx.markAttributeMock("foo.bar");
		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", null, null, variables))
				.then(() -> attrCtx.mockEmit("foo.bar", Val.of(1))).expectNext(Val.of(1)).thenCancel().verify();
	}

	@Test
	void test_dynamicMock_duplicateRegistration() {
		attrCtx.markAttributeMock("foo.bar");
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo.bar"));
	}

	@Test
	void test_dynamicMock_mockEmitCalledForInvalidFullName() {
		attrCtx.loadAttributeMock("test.test", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.mockEmit("test.test", Val.of(1)));
	}

	@Test
	void test_dynamicMock_ForEnvironmentAttribute() {
		attrCtx.markAttributeMock("foo.bar");
		StepVerifier.create(attrCtx.evaluateEnvironmentAttribute("foo.bar", null, variables))
				.then(() -> attrCtx.mockEmit("foo.bar", Val.of(1))).expectNext(Val.of(1)).thenCancel().verify();
	}

	@Test
	void test_timingMock() {
		attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		StepVerifier.withVirtualTime(() -> attrCtx.evaluateAttribute("foo.bar", null, null, variables))
				.thenAwait(Duration.ofSeconds(10)).expectNext(Val.of(1)).thenAwait(Duration.ofSeconds(10))
				.expectNext(Val.of(2)).verifyComplete();
	}

	@Test
	void test_timingMock_duplicateRegistration() {
		attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2)));
	}

	@Test
	void test_timingMock_ForEnvironmentAttribute() {
		attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		StepVerifier.withVirtualTime(() -> attrCtx.evaluateEnvironmentAttribute("foo.bar", null, variables))
				.thenAwait(Duration.ofSeconds(10)).expectNext(Val.of(1)).thenAwait(Duration.ofSeconds(10))
				.expectNext(Val.of(2)).verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValue() {
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2));
		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", Val.of(1), null, variables)).expectNext(Val.of(2))
				.verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValue_duplicateRegistration() {
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2));
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(2)), Val.of(3));
		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", Val.of(1), null, variables)).expectNext(Val.of(2))
				.verifyComplete();
		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", Val.of(2), null, variables)).expectNext(Val.of(3))
				.verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValue_registeredButWrongType() {
		attrCtx.markAttributeMock("foo.bar");
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2)));
	}

	@Test
	void test_ForParentValue_ForEnvironmentAttribute() {
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(is(Val.UNDEFINED)), Val.of(2));
		StepVerifier.create(attrCtx.evaluateEnvironmentAttribute("foo.bar", null, variables)).expectNext(Val.of(2))
				.verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValueAndArguments() {
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
				whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(2));

		var expression = Mockito.mock(Expression.class);
		Mockito.when(expression.evaluate()).thenReturn(Flux.just(Val.TRUE));
		var arguments = Mockito.mock(Arguments.class);
		Mockito.when(arguments.getArgs()).thenReturn(new BasicEList<>(List.of(expression)));

		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", Val.of(1), arguments, variables)).expectNext(Val.of(2))
				.verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValueAndArguments_duplicateRegistration() {
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
				whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(0));
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
				whenAttributeParams(parentValue(val(1)), arguments(val(false))), Val.of(1));

		var expression = Mockito.mock(Expression.class);
		Mockito.when(expression.evaluate()).thenReturn(Flux.just(Val.TRUE));
		var arguments = Mockito.mock(Arguments.class);
		Mockito.when(arguments.getArgs()).thenReturn(new BasicEList<>(List.of(expression)));

		StepVerifier.create(attrCtx.evaluateAttribute("foo.bar", Val.of(1), arguments, variables)).expectNext(Val.of(0))
				.verifyComplete();
	}

	@Test
	void test_loadAttributeMockForParentValueAndArguments_registeredButWrongType() {
		attrCtx.markAttributeMock("foo.bar");
		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
						whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(2)));
	}

	@Test
	void test_ForParentValueAndArguments_ForEnvironmentAttribute() {
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
				whenAttributeParams(parentValue(is(Val.UNDEFINED)), arguments(val(true))), Val.of(2));

		var expression = Mockito.mock(Expression.class);
		Mockito.when(expression.evaluate()).thenReturn(Flux.just(Val.TRUE));
		var arguments = Mockito.mock(Arguments.class);
		Mockito.when(arguments.getArgs()).thenReturn(new BasicEList<>(List.of(expression)));

		StepVerifier.create(attrCtx.evaluateEnvironmentAttribute("foo.bar", arguments, variables)).expectNext(Val.of(2))
				.verifyComplete();
	}

	@Test
	void test_invalidFullName() {
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo"));
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo.bar.xxx"));
	}

	@Test
	void test_isProvided_False() {
		assertThat(this.attrCtx.isProvidedFunction("foo.bar")).isFalse();
	}

	@Test
	void test_test_isProvided() {
		when(unmockedCtx.isProvidedFunction("iii.iii")).thenReturn(Boolean.TRUE);
		when(unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("xxx")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("iii")).thenReturn(List.of("iii", "iiii"));

		attrCtx.markAttributeMock("foo.bar");
		attrCtx.markAttributeMock("foo.abc");
		attrCtx.markAttributeMock("xxx.xxx");
		attrCtx.markAttributeMock("xxx.yyy");
		attrCtx.markAttributeMock("xxx.zzz");

		assertThat(attrCtx.isProvidedFunction("foo.bar")).isTrue();
		assertThat(attrCtx.isProvidedFunction("foo.abc")).isTrue();
		assertThat(attrCtx.isProvidedFunction("xxx.xxx")).isTrue();
		assertThat(attrCtx.isProvidedFunction("xxx.yyy")).isTrue();
		assertThat(attrCtx.isProvidedFunction("xxx.zzz")).isTrue();
		assertThat(attrCtx.isProvidedFunction("iii.iii")).isTrue();

		assertThat(attrCtx.providedFunctionsOfLibrary("foo")).containsAll(List.of("bar", "abc"));
		assertThat(attrCtx.providedFunctionsOfLibrary("xxx")).containsAll(List.of("xxx", "yyy", "zzz"));
		assertThat(attrCtx.providedFunctionsOfLibrary("iii")).containsAll(List.of("iii", "iiii"));

	}

	@Test
	void test_ReturnUnmockedEvaluation() {
		when(unmockedCtx.evaluateAttribute(any(), any(), any(), any())).thenReturn(Val.fluxOf("abc"));
		StepVerifier.create(this.attrCtx.evaluateAttribute("foo.bar", null, null, null)).expectNext(Val.of("abc"))
				.expectComplete().verify();
	}

	@Test
	void test_documentation() {
		this.attrCtx.markAttributeMock("foo.bar");
		var unmockedDoc = new PolicyInformationPointDocumentation("test", "Test", new TestPIP());
		unmockedDoc.getDocumentation().put("upper", "blabla");
		when(this.unmockedCtx.getDocumentation()).thenReturn(List.of(unmockedDoc));

		Collection<PolicyInformationPointDocumentation> result = this.attrCtx.getDocumentation();

		assertThat(result).hasSize(2);
		var iterator = result.iterator();
		var doc1     = iterator.next();
		assertThat(doc1.getName()).isEqualTo("foo");
		assertThat(doc1.getDocumentation()).containsKey("bar");
		var doc2 = iterator.next();
		assertThat(doc2.getName()).isEqualTo("test");
		assertThat(doc2.getDocumentation()).containsKey("upper");
	}

	@Test
	void test_IsProvidedFunctionOfLibrary() {
		this.attrCtx.markAttributeMock("foo.bar");
		when(this.unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of("bar", "xxx", "yyy"));

		Collection<String> result = this.attrCtx.providedFunctionsOfLibrary("foo");

		assertThat(result).hasSize(3).containsOnly("bar", "xxx", "yyy");
	}

	@Test
	void test_mockEmit_UnmockedAttribute() {
		var unmockedCtx = new AnnotationAttributeContext();
		var ctx         = new MockingAttributeContext(unmockedCtx);

		assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.mockEmit("foo.bar", Val.of(1)));
	}

	@Test
	void test_getAvailableLibraries_returnsAllAvailableLibraries() {
		this.attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		assertThat(this.attrCtx.getAvailableLibraries()).containsOnly("foo.bar");
	}

}
