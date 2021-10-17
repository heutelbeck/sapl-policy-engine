package io.sapl.test.mocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.unit.TestPIP;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.test.StepVerifier;

public class MockingAttributeContextTest {

	AttributeContext unmockedCtx;
	MockingAttributeContext ctx;

	@BeforeEach
	void setup() {
		this.unmockedCtx = Mockito.mock(AnnotationAttributeContext.class);
		this.ctx = new MockingAttributeContext(unmockedCtx);
	}

	@Test
	void test_dynamicMock() {
		ctx.markAttributeMock("foo.bar");
		StepVerifier.create(ctx.evaluate("foo.bar", null, null, null))
			.then(() -> ctx.mockEmit("foo.bar", Val.of(1)))
			.expectNext(Val.of(1))
			.thenCancel().verify();
	}
	
	@Test
	void test_dynamicMock_duplicateRegistration() {
		ctx.markAttributeMock("foo.bar");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> ctx.markAttributeMock("foo.bar"));
	}
	
	@Test
	void test_dynamicMock_mockEmitCalledForInvalidFullname() {
		ctx.loadAttributeMock("test.test", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> ctx.mockEmit("test.test", Val.of(1)));
	}
	
	@Test
	void test_timingMock() {
		ctx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		Assertions.assertThat(ctx.evaluate("foo.bar", null, null, null)).isNotNull();
	}

	@Test
	void test_invalidFullname() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.markAttributeMock("foo"));
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.markAttributeMock("foo.bar.xxx"));
	}

	@Test
	void test_isProvided_False() {
		Assertions.assertThat(this.ctx.isProvidedFunction("foo.bar")).isFalse();
	}

	@Test
	void test_test_isProvided() {
		when(unmockedCtx.isProvidedFunction("iii.iii")).thenReturn(true);
		when(unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("xxx")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("iii")).thenReturn(List.of("iii", "iiii"));

		ctx.markAttributeMock("foo.bar");
		ctx.markAttributeMock("foo.abc");
		ctx.markAttributeMock("xxx.xxx");
		ctx.markAttributeMock("xxx.yyy");
		ctx.markAttributeMock("xxx.zzz");

		Assertions.assertThat(ctx.isProvidedFunction("foo.bar")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.abc")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("xxx.xxx")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("xxx.yyy")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("xxx.zzz")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("iii.iii")).isTrue();

		Assertions.assertThat(ctx.providedFunctionsOfLibrary("foo")).containsAll(List.of("bar", "abc"));
		Assertions.assertThat(ctx.providedFunctionsOfLibrary("xxx")).containsAll(List.of("xxx", "yyy", "zzz"));
		Assertions.assertThat(ctx.providedFunctionsOfLibrary("iii")).containsAll(List.of("iii", "iiii"));

	}

	@Test
	void test_ReturnUnmockedEvaluation() {
		when(unmockedCtx.evaluate(any(), any(), any(), any())).thenReturn(Val.fluxOf("abc"));
		StepVerifier.create(this.ctx.evaluate("foo.bar", null, null, null)).expectNext(Val.of("abc")).expectComplete()
				.verify();
	}

	@Test
	void test_documentation() {
		this.ctx.markAttributeMock("foo.bar");
		var unmockedDoc = new PolicyInformationPointDocumentation("test", "Test", new TestPIP());
		unmockedDoc.getDocumentation().put("upper", "blabla");
		when(this.unmockedCtx.getDocumentation()).thenReturn(List.of(unmockedDoc));

		Collection<PolicyInformationPointDocumentation> result = this.ctx.getDocumentation();

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
		this.ctx.markAttributeMock("foo.bar");
		when(this.unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of("bar", "xxx", "yyy"));

		Collection<String> result = this.ctx.providedFunctionsOfLibrary("foo");

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
		this.ctx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
		assertThat(this.ctx.getAvailableLibraries()).containsOnly("foo.bar");
	}
	
	@Test
	void test_loadPolicyInformationPoint() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> this.ctx.loadPolicyInformationPoint(new ClockPolicyInformationPoint()));
	}
}
