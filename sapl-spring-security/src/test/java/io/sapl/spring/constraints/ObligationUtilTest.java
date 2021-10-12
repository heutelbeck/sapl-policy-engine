package io.sapl.spring.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.security.access.AccessDeniedException;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ObligationUtilTest {

	private static final String HANDLER_FAILURE = "HANDLER FAILURE";

	@Test
	void when_consumerIsNull_then_returnEmpty() {
		assertTrue(ObligationUtil.obligation((Consumer<?>) null).isEmpty());
	}

	@Test
	void when_consumerIsNotNull_then_returnOptionalOfConsumer() {
		Consumer<?> consumer = mock(Consumer.class);
		assertTrue(ObligationUtil.obligation((Consumer<?>) consumer).isPresent());
	}

	@Test
	void when_consumerIsNotNull_then_invokingTheWrappedConsumerInvokesOriginalConsumer() {
		Consumer<?> consumer = mock(Consumer.class);
		ObligationUtil.obligation((Consumer<?>) consumer).get().accept(null);
		verify(consumer, times(1)).accept(any());
	}

	@Test
	void when_consumerFails_then_errorIsWrappedInAccessDeniedException() {
		Consumer<?> consumer = mock(Consumer.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(consumer).accept(any());
		assertThrows(AccessDeniedException.class,
				() -> ObligationUtil.obligation((Consumer<?>) consumer).get().accept(null));
	}

	@Test
	void when_longConsumerIsNull_then_returnEmpty() {
		assertTrue(ObligationUtil.obligation((LongConsumer) null).isEmpty());
	}

	@Test
	void when_longConsumerIsNotNull_then_returnOptionalOfConsumer() {
		LongConsumer consumer = mock(LongConsumer.class);
		assertTrue(ObligationUtil.obligation(consumer).isPresent());
	}

	@Test
	void when_longConsumerIsNotNull_then_invokingTheWrappedConsumerInvokesOriginalConsumer() {
		LongConsumer consumer = mock(LongConsumer.class);
		ObligationUtil.obligation(consumer).get().accept(0L);
		verify(consumer, times(1)).accept(anyLong());
	}

	@Test
	void when_longConsumerFails_then_errorIsWrappedInAccessDeniedException() {
		LongConsumer consumer = mock(LongConsumer.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(consumer).accept(anyLong());
		assertThrows(AccessDeniedException.class, () -> ObligationUtil.obligation(consumer).get().accept(0L));
	}

	@Test
	void when_functionIsNull_then_returnEmpty() {
		assertTrue(ObligationUtil.obligation((Function) null).isEmpty());
	}

	@Test
	void when_functionIsNotNull_then_returnOptionalOfFunction() {
		Function function = mock(Function.class);
		assertTrue(ObligationUtil.obligation(function).isPresent());
	}

	@Test
	void when_functionIsNotNullButReturnsNull_then_wrappedFunctionInvokesOriginalFunctionAndAccessDenied() {
		Function function = mock(Function.class);
		when(function.apply(any())).thenReturn(null);
		assertThrows(AccessDeniedException.class,
				() -> ((Function) ObligationUtil.obligation(function).get()).apply(null));
		verify(function, times(1)).apply(any());
	}

	@Test
	void when_functionIsNotNull_then_invokesWrappedFunction() {
		Function<String, String> function = mock(Function.class);
		when(function.apply(any())).thenReturn("RESULT");
		assertEquals("RESULT", ((Function) ObligationUtil.obligation(function).get()).apply("INPUT"));
		verify(function, times(1)).apply(any());
	}

	@Test
	void when_functionFails_then_errorIsWrappedInAccessDeniedException() {
		Function function = mock(Function.class);
		when(function.apply(any())).thenThrow(new IllegalStateException(HANDLER_FAILURE));
		assertThrows(AccessDeniedException.class,
				() -> ((Function) ObligationUtil.obligation(function).get()).apply(null));
	}

	@Test
	void when_runnableIsNull_then_returnEmpty() {
		assertTrue(ObligationUtil.obligation((Runnable) null).isEmpty());
	}

	@Test
	void when_runnableIsNotNull_then_returnOptionalOfRunnable() {
		Runnable runnable = mock(Runnable.class);
		assertTrue(ObligationUtil.obligation(runnable).isPresent());
	}

	@Test
	void when_runnableIsNotNull_then_invokingTheWrappedRunnableInvokesOriginalRunnable() {
		Runnable runnable = mock(Runnable.class);
		ObligationUtil.obligation(runnable).get().run();
		verify(runnable, times(1)).run();
	}

	@Test
	void when_runnableFails_then_errorIsWrappedInAccessDeniedException() {
		Runnable runnable = mock(Runnable.class);
		doThrow(new IllegalStateException(HANDLER_FAILURE)).when(runnable).run();
		assertThrows(AccessDeniedException.class, () -> ObligationUtil.obligation(runnable).get().run());
	}

}
