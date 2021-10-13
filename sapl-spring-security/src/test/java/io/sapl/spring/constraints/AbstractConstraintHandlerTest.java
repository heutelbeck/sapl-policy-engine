package io.sapl.spring.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

public class AbstractConstraintHandlerTest {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	private final static JsonNode CONSTRAINT;

	static {
		try {
			CONSTRAINT = MAPPER.readValue("\"primitive constraint\"", JsonNode.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void when_compared_orderMatchesPriority() {
		var serviceZero = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};
		var serviceOne = new AbstractConstraintHandler(1) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};

		assertEquals(0, serviceZero.compareTo(serviceZero));
		assertEquals(-1, serviceZero.compareTo(serviceOne));
		assertEquals(1, serviceOne.compareTo(serviceZero));
	}

	@Test
	void when_allHandlingMethodsReturnNull_fluxIsNotChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onSubscribe_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(1)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onNext_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public <T> Consumer<T> onNext(JsonNode constraint) {
				return __ -> {
				};
			}
		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(1)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onError_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<? super Throwable> onError(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(1)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onComplete_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable onComplete(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(1)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onAfterTerminate_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable afterTerminate(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(1)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onCancel_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable onCancel(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(1)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onRequest_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Long> onRequest(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(1)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onNextMap_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public <T> Function<T, T> onNextMap(JsonNode constraint) {
				return t -> t;
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(1)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onErrorMap_fluxIsChanged() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Function<Throwable, Throwable> onErrorMap(JsonNode constraint) {
				return t -> t;
			}

		};
		service.applyObligation(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(1)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_allHandlingMethodsReturnNull_fluxIsNotChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onSubscribe_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(1)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onNext_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public <T> Consumer<T> onNext(JsonNode constraint) {
				return __ -> {
				};
			}
		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(1)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onError_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<? super Throwable> onError(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(1)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onComplete_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable onComplete(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(1)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onAfterTerminate_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable afterTerminate(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(1)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onCancel_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Runnable onCancel(JsonNode constraint) {
				return () -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(1)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onRequest_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Long> onRequest(JsonNode constraint) {
				return __ -> {
				};
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(1)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onNextMap_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public <T> Function<T, T> onNextMap(JsonNode constraint) {
				return t -> t;
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(1)).map(any());
		verify(spy, times(0)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_onErrorMap_fluxIsChanged_forAdvice() {
		Flux<Object> spy = spy(Flux.just(1, 2, 3));

		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Function<Throwable, Throwable> onErrorMap(JsonNode constraint) {
				return t -> t;
			}

		};
		service.applyAdvice(spy, CONSTRAINT);
		verify(spy, times(0)).doOnSubscribe(any());
		verify(spy, times(0)).doOnNext(any());
		verify(spy, times(0)).doOnError(any());
		verify(spy, times(0)).doOnComplete(any());
		verify(spy, times(0)).doAfterTerminate(any());
		verify(spy, times(0)).doOnCancel(any());
		verify(spy, times(0)).doOnRequest(any());
		verify(spy, times(0)).map(any());
		verify(spy, times(1)).onErrorMap(any());
		verify(spy, times(0)).flatMap(any());
	}

	@Test
	void when_notImplemented_preBlockingMethodInvocationOrOnAccessDenied_returnsFalse() {
		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return false;
			}
		};

		assertFalse(service.preBlockingMethodInvocationOrOnAccessDenied(CONSTRAINT));
	}

	@Test
	void when_notImplemented_postBlockingMethodInvocation_returnsNull() {
		var service = new AbstractConstraintHandler(0) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return false;
			}
		};

		assertNull(service.postBlockingMethodInvocation(CONSTRAINT));
	}

}
