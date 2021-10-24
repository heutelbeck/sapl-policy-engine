package io.sapl.test.mocking.attribute;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.MockingFunctionContext;
import io.sapl.test.unit.TestPIP;

import org.assertj.core.api.Assertions;
import org.eclipse.emf.common.util.BasicEList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class MockingAttributeContextTest {

	EvaluationContext ctx;
	AttributeContext unmockedCtx;
	MockingAttributeContext attrCtx;

	@BeforeEach
	void setup() {
		this.unmockedCtx = Mockito.mock(AnnotationAttributeContext.class);
		this.attrCtx = new MockingAttributeContext(unmockedCtx);
		this.ctx = new EvaluationContext(this.attrCtx, new MockingFunctionContext(null), new HashMap<>());
	}

	@Test
	void test_dynamicMock() {
		attrCtx.markAttributeMock("foo.bar");
		StepVerifier.create(attrCtx.evaluate("foo.bar", null, this.ctx, null))
			.then(() -> attrCtx.mockEmit("foo.bar", Val.of(1)))
			.expectNext(Val.of(1))
			.thenCancel().verify();
	}
	
	@Test
	void test_dynamicMock_duplicateRegistration() {
		attrCtx.markAttributeMock("foo.bar");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> attrCtx.markAttributeMock("foo.bar"));
	}
	
	@Test
	void test_dynamicMock_mockEmitCalledForInvalidFullname() {
		attrCtx.loadAttributeMock("test.test", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> attrCtx.mockEmit("test.test", Val.of(1)));
	}
	
	@Test
	void test_timingMock() {
		attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		Assertions.assertThat(attrCtx.evaluate("foo.bar", null, this.ctx, null)).isNotNull();
	}
	@Test
	void test_timingMock_duplicateRegistration() {
		attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2)));
	}
	

	@Test
	void test_loadAttributeMockForParentValue() {
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2));
		Assertions.assertThat(attrCtx.evaluate("foo.bar", Val.of(1), this.ctx, null)).isNotNull();
	}
	
	@Test
	void test_loadAttributeMockForParentValue_duplicateRegistration() {
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2));
		attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(2)), Val.of(3));
		Assertions.assertThat(attrCtx.evaluate("foo.bar", Val.of(1), this.ctx, null)).isNotNull();
	}
	
	@Test
	void test_loadAttributeMockForParentValue_registeredButWrongType() {
		attrCtx.markAttributeMock("foo.bar");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> attrCtx.loadAttributeMockForParentValue("foo.bar", parentValue(val(1)), Val.of(2)));
	}
	
	
	
	@Test
	void test_loadAttributeMockForParentValueAndArguments() {
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar", whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(2));
		
		Expression expression = Mockito.mock(Expression.class);
		Mockito.when(expression.evaluate(any(),any())).thenReturn(Flux.just(Val.TRUE));
		Arguments arguments = Mockito.mock(Arguments.class);
		Mockito.when(arguments.getArgs()).thenReturn(new BasicEList<Expression>(List.of(expression)));
		
		Assertions.assertThat(attrCtx.evaluate("foo.bar", Val.of(1), this.ctx, arguments)).isNotNull();
	}
	
	@Test
	void test_loadAttributeMockForParentValueAndArguments_duplicateRegistration() {
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar", whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(0));
		attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar", whenAttributeParams(parentValue(val(1)), arguments(val(false))), Val.of(1));

		Expression expression = Mockito.mock(Expression.class);
		Mockito.when(expression.evaluate(any(),any())).thenReturn(Flux.just(Val.TRUE));
		Arguments arguments = Mockito.mock(Arguments.class);
		Mockito.when(arguments.getArgs()).thenReturn(new BasicEList<Expression>(List.of(expression)));
		
		Assertions.assertThat(attrCtx.evaluate("foo.bar", Val.of(1), this.ctx, arguments)).isNotNull();
	}
	
	@Test
	void test_loadAttributeMockForParentValueAndArguments_registeredButWrongType() {
		attrCtx.markAttributeMock("foo.bar");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar", whenAttributeParams(parentValue(val(1)), arguments(val(true))), Val.of(2)));
	}

	@Test
	void test_invalidFullname() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo"));
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo.bar.xxx"));
	}

	@Test
	void test_isProvided_False() {
		Assertions.assertThat(this.attrCtx.isProvidedFunction("foo.bar")).isFalse();
	}

	@Test
	void test_test_isProvided() {
		when(unmockedCtx.isProvidedFunction("iii.iii")).thenReturn(true);
		when(unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("xxx")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("iii")).thenReturn(List.of("iii", "iiii"));

		attrCtx.markAttributeMock("foo.bar");
		attrCtx.markAttributeMock("foo.abc");
		attrCtx.markAttributeMock("xxx.xxx");
		attrCtx.markAttributeMock("xxx.yyy");
		attrCtx.markAttributeMock("xxx.zzz");

		Assertions.assertThat(attrCtx.isProvidedFunction("foo.bar")).isTrue();
		Assertions.assertThat(attrCtx.isProvidedFunction("foo.abc")).isTrue();
		Assertions.assertThat(attrCtx.isProvidedFunction("xxx.xxx")).isTrue();
		Assertions.assertThat(attrCtx.isProvidedFunction("xxx.yyy")).isTrue();
		Assertions.assertThat(attrCtx.isProvidedFunction("xxx.zzz")).isTrue();
		Assertions.assertThat(attrCtx.isProvidedFunction("iii.iii")).isTrue();

		Assertions.assertThat(attrCtx.providedFunctionsOfLibrary("foo")).containsAll(List.of("bar", "abc"));
		Assertions.assertThat(attrCtx.providedFunctionsOfLibrary("xxx")).containsAll(List.of("xxx", "yyy", "zzz"));
		Assertions.assertThat(attrCtx.providedFunctionsOfLibrary("iii")).containsAll(List.of("iii", "iiii"));

	}

	@Test
	void test_ReturnUnmockedEvaluation() {
		when(unmockedCtx.evaluate(any(), any(), any(), any())).thenReturn(Val.fluxOf("abc"));
		StepVerifier.create(this.attrCtx.evaluate("foo.bar", null, null, null)).expectNext(Val.of("abc")).expectComplete()
				.verify();
	}

	@Test
	void test_documentation() {
		this.attrCtx.markAttributeMock("foo.bar");
		var unmockedDoc = new PolicyInformationPointDocumentation("test", "Test", new TestPIP());
		unmockedDoc.getDocumentation().put("upper", "blabla");
		when(this.unmockedCtx.getDocumentation()).thenReturn(List.of(unmockedDoc));

		Collection<PolicyInformationPointDocumentation> result = this.attrCtx.getDocumentation();

		Assertions.assertThat(result.size()).isEqualTo(2);
		var iterator = result.iterator();
		var doc1 = iterator.next();
		Assertions.assertThat(doc1.getName()).isEqualTo("foo");
		Assertions.assertThat(doc1.getDocumentation().containsKey("bar")).isTrue();
		var doc2 = iterator.next();
		Assertions.assertThat(doc2.getName()).isEqualTo("test");
		Assertions.assertThat(doc2.getDocumentation().containsKey("upper")).isTrue();
	}

	@Test
	void test_IsProvidedFunctionOfLibrary() {
		this.attrCtx.markAttributeMock("foo.bar");
		when(this.unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of("bar", "xxx", "yyy"));

		Collection<String> result = this.attrCtx.providedFunctionsOfLibrary("foo");

		Assertions.assertThat(result.size()).isEqualTo(3);
		Assertions.assertThat(result).containsOnly("bar", "xxx", "yyy");
	}

	@Test
	void test_mockEmit_UnmockedAttribute() {
		AttributeContext unmockedCtx = new AnnotationAttributeContext();
		MockingAttributeContext ctx = new MockingAttributeContext(unmockedCtx);

		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ctx.mockEmit("foo.bar", Val.of(1)));
	}
	
	@Test
	void test_getAvailableLibraries_returnsAllAvailableLibraries() {	
		this.attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		assertThat(this.attrCtx.getAvailableLibraries()).containsOnly("foo.bar");
	}
	
	@Test
	void test_loadPolicyInformationPoint() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> this.attrCtx.loadPolicyInformationPoint(new ClockPolicyInformationPoint()));
	}
}
