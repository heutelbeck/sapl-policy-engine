package io.sapl.spring.constraints;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class AdviceUtilTest {

	private static final String HANDLER_FAILURE = "HANDLER FAILURE";

	@Test
	void when_consumerIsNull_then_returnEmpty() {
		assertTrue(AdviceUtil.advice((Consumer<?>) null).isEmpty());
	}

	@Test
	void when_consumerIsNotNull_then_returnOptionalOfConsumer() {
		Consumer<?> consumer = mock(Consumer.class);
		assertTrue(AdviceUtil.advice((Consumer<?>) consumer).isPresent());
	}

	@Test
	void when_consumerIsNotNull_then_invokingTheWrappedConsumerInvokesOriginalConsumer() {
		Consumer<?> consumer = mock(Consumer.class);
		AdviceUtil.advice((Consumer<?>) consumer).get().accept(null);
		verify(consumer, times(1)).accept(any());
	}

	@Test
	void when_consumerFails_then_errorIsIgnored() {
		Consumer<?> consumer = mock(Consumer.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(consumer).accept(any());
		assertDoesNotThrow(() -> AdviceUtil.advice((Consumer<?>) consumer).get().accept(null));
	}

	@Test
	void when_longConsumerIsNull_then_returnEmpty() {
		assertTrue(AdviceUtil.advice((LongConsumer) null).isEmpty());
	}

	@Test
	void when_longConsumerIsNotNull_then_returnOptionalOfConsumer() {
		LongConsumer consumer = mock(LongConsumer.class);
		assertTrue(AdviceUtil.advice(consumer).isPresent());
	}

	@Test
	void when_longConsumerIsNotNull_then_invokingTheWrappedConsumerInvokesOriginalConsumer() {
		LongConsumer consumer = mock(LongConsumer.class);
		AdviceUtil.advice(consumer).get().accept(0L);
		verify(consumer, times(1)).accept(anyLong());
	}

	@Test
	void when_longConsumerFails_then_errorIsIgnored() {
		LongConsumer consumer = mock(LongConsumer.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(consumer).accept(anyLong());
		assertDoesNotThrow(() -> AdviceUtil.advice(consumer).get().accept(0L));
	}

	@Test
	void when_functionIsNull_then_returnEmpty() {
		assertTrue(AdviceUtil.advice((Function) null).isEmpty());
	}

	@Test
	void when_functionIsNotNull_then_returnOptionalOfFunction() {
		Function function = mock(Function.class);
		assertTrue(AdviceUtil.advice(function).isPresent());
	}

	@Test
	void when_functionIsNotNullButReturnsNull_then_wrappedFunctionInvokesOriginalFunctionButFallsBackToIdentity() {
		Function<String, String> function = mock(Function.class);
		when(function.apply(any())).thenReturn(null);
		assertEquals("INPUT", ((Function) AdviceUtil.advice(function).get()).apply("INPUT"));
		verify(function, times(1)).apply(any());
	}

	@Test
	void when_functionIsNotNull_then_invokesWrappedFunction() {
		Function<String, String> function = mock(Function.class);
		when(function.apply(any())).thenReturn("RESULT");
		assertEquals("RESULT", ((Function) AdviceUtil.advice(function).get()).apply("INPUT"));
		verify(function, times(1)).apply(any());
	}

	@Test
	void when_functionFails_then_errorIsIgnoredAndFallsBackToIdentity() {
		Function<String, String> function = mock(Function.class);
		when(function.apply(any())).thenThrow(new IllegalStateException(HANDLER_FAILURE));
		assertEquals("INPUT", ((Function) AdviceUtil.advice(function).get()).apply("INPUT"));
	}

	@Test
	void when_runnableIsNull_then_returnEmpty() {
		assertTrue(AdviceUtil.advice((Runnable) null).isEmpty());
	}

	@Test
	void when_runnableIsNotNull_then_returnOptionalOfRunnable() {
		Runnable runnable = mock(Runnable.class);
		assertTrue(AdviceUtil.advice(runnable).isPresent());
	}

	@Test
	void when_runnableIsNotNull_then_invokingTheWrappedRunnableInvokesOriginalRunnable() {
		Runnable runnable = mock(Runnable.class);
		AdviceUtil.advice(runnable).get().run();
		verify(runnable, times(1)).run();
	}

	@Test
	void when_runnableFails_then_errorIsIgnored() {
		Runnable runnable = mock(Runnable.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(runnable).run();
		assertDoesNotThrow(() -> AdviceUtil.advice(runnable).get().run());
	}

	@Test
	void when_functionIsNull_then_flatMapReturnEmpty() {
		assertTrue(AdviceUtil.flatMapAdvice((Function) null).isEmpty());
	}

	@Test
	void when_functionIsNotNull_then_flatMapReturnOptionalOfFunction() {
		Function function = mock(Function.class);
		assertTrue(AdviceUtil.flatMapAdvice(function).isPresent());
	}

	@Test
	void when_functionIsNotNullButReturnsNull_then_flatMapInvokingTheWrappedFunctionInvokesOriginalFunctionFallsBackToIdentity() {
		Function<String, Publisher<String>> function = mock(Function.class);
		when(function.apply(any())).thenReturn(null);
		var result = (Publisher<String>) ((Function<String, Publisher<String>>) AdviceUtil.flatMapAdvice(function).get())
				.apply("INPUT");
		StepVerifier.create(result).expectNext("INPUT").verifyComplete();
		verify(function, times(1)).apply(any());
	}
	
	@Test
	void when_functionIsNotNullButPublisherHasError_then_flatMapInvokingTheWrappedFunctionInvokesOriginalFunctionFallsBackToIdentity() {
		Function<String, Publisher<String>> function = mock(Function.class);
		when(function.apply(any())).thenReturn(Mono.error(new IllegalStateException(HANDLER_FAILURE)));
		var result = (Publisher<String>) ((Function<String, Publisher<String>>) AdviceUtil.flatMapAdvice(function).get())
				.apply("INPUT");
		StepVerifier.create(result).expectNext("INPUT").verifyComplete();
		verify(function, times(1)).apply(any());
	}

	@Test
	void when_functionIsNotNull_then_flatMapInvokingTheWrappedFunction() {
		Function function = mock(Function.class);
		when(function.apply(any())).thenReturn(Flux.empty());

		var result = (Publisher) ((Function) AdviceUtil.flatMapAdvice(function).get()).apply(null);
		StepVerifier.create(result).verifyComplete();

		verify(function, times(1)).apply(any());
	}

}
