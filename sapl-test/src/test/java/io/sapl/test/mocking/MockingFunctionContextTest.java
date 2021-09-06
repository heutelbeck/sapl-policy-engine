package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;
import static io.sapl.test.Imports.whenParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.unit.TestPIP;

public class MockingFunctionContextTest {

	FunctionContext unmockedCtx;
	MockingFunctionContext ctx;

	@BeforeEach
	void setup() {
		this.unmockedCtx = Mockito.mock(AnnotationFunctionContext.class);
		this.ctx = new MockingFunctionContext(unmockedCtx);
	}

	@Test
	void test_invalidFullname() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.checkImportName("foo"));
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.checkImportName("foo.bar.xxx"));
	}

	@Test
	void test_isProvided_False() {
		Assertions.assertThat(this.ctx.isProvidedFunction("foo.bar")).isFalse();
	}
	
	@Test
	void test_FunctionMockAlwaysSameValue_duplicateRegistration() {
		this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1)));
	}

	
	@Test
	void test_alreadyDefinedMock_NotFunctionMockSequence() {
		this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> this.ctx.loadFunctionMockReturnsSequence("foo.bar", new Val[] {Val.of(1)}));
	}
	
	@Test
	void test_alreadyDefinedMock_NotFunctionMockAlwaysSameForParameters() {
		this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> this.ctx.loadFunctionMockAlwaysSameValueForParameters("foo.bar", Val.of(1), whenParameters(is(Val.of(1)))));
	}
	
	@Test
	void test_FunctionMockValueFromFunction_duplicateRegistration() {
		this.ctx.loadFunctionMockValueFromFunction("foo.bar", (call) -> Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> this.ctx.loadFunctionMockValueFromFunction("foo.bar", (call) -> Val.of(1)));
	}
	
	
	@Test
	void test_isProvided() {
		when(unmockedCtx.isProvidedFunction("iii.iii")).thenReturn(true);
		when(unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("xxx")).thenReturn(List.of());
		when(unmockedCtx.providedFunctionsOfLibrary("iii")).thenReturn(List.of("iii", "iiii"));

		ctx.loadFunctionMockValueFromFunction("foo.bar", (call) -> Val.of("1"));
		ctx.loadFunctionMockValueFromFunction("foo.abc",  (call) -> Val.of("1"), times(1));
		ctx.loadFunctionMockAlwaysSameValue("xxx.xxx", Val.of(1));
		ctx.loadFunctionMockAlwaysSameValue("xxx.yyy", Val.of(1), times(1));
		ctx.loadFunctionMockAlwaysSameValueForParameters("foo.123", Val.of(1), whenParameters());
		ctx.loadFunctionMockAlwaysSameValueForParameters("foo.456", Val.of(1), whenParameters(), times(1));
		ctx.loadFunctionMockOnceReturnValue("foo.789", Val.of(1));
		ctx.loadFunctionMockReturnsSequence("foo.987", new Val[] {Val.of(1)});

		Assertions.assertThat(ctx.isProvidedFunction("foo.bar")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.abc")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("xxx.xxx")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("xxx.yyy")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.123")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.456")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.789")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("foo.987")).isTrue();
		Assertions.assertThat(ctx.isProvidedFunction("iii.iii")).isTrue();

		Assertions.assertThat(ctx.providedFunctionsOfLibrary("foo")).containsAll(List.of("bar", "abc", "123", "456", "789", "987"));
		Assertions.assertThat(ctx.providedFunctionsOfLibrary("xxx")).containsAll(List.of("xxx", "yyy"));
		Assertions.assertThat(ctx.providedFunctionsOfLibrary("iii")).containsAll(List.of("iii", "iiii"));

	}

	@Test
	void test_ReturnUnmockedEvaluation() {
		when(unmockedCtx.evaluate(any(), any(), any(), any())).thenReturn(Val.of("abc"));
		Assertions.assertThat(this.ctx.evaluate("foo.bar", null, null, null)).isEqualTo(Val.of("abc"));
	}

	@Test
	void test_documentation() {
		this.ctx.addNewLibraryDocumentation("foo.bar", new FunctionMockAlwaysSameValue("foo.bar", Val.of(1), times(1)));
		var unmockedDoc = new LibraryDocumentation("test", "Test", new TestPIP());
		unmockedDoc.getDocumentation().put("upper", "blabla");
		when(this.unmockedCtx.getDocumentation()).thenReturn(List.of(unmockedDoc));

		Collection<LibraryDocumentation> result = this.ctx.getDocumentation();

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
	void test_getAvailableLibraries_returnsAllAvailableLibraries() {	
		ctx.loadFunctionMockValueFromFunction("foo.bar", (call) -> Val.of("1"));
		assertThat(this.ctx.getAvailableLibraries()).containsOnly("foo.bar");
	}	
	
	@Test
	void test_loadFunctionLibrary() {
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> this.ctx.loadLibrary(new TemporalFunctionLibrary()));
	}
}
