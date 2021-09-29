package io.sapl.spring.constraints;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class ObligationUtil {
	public static <T> Optional<Consumer<? super T>> obligation(Consumer<? super T> consumer) {
		if (consumer == null)
			return Optional.empty();

		return Optional.of(t -> {
			try {
				consumer.accept(t);
			} catch (Throwable e) {
				throw new AccessDeniedException("Error during obligation handling: " + e.getMessage(), e);
			}
		});
	}

	public static <T> Optional<Function<T, T>> obligation(Function<T, T> function) {
		if (function == null)
			return Optional.empty();

		return Optional.of(t -> {
			try {
				var result = function.apply(t);
				if (result == null)
					throw new IllegalStateException("Handler function must not return null");
				return result;
			} catch (Throwable e) {
				throw new AccessDeniedException("Error during obligation handling: " + e.getMessage(), e);
			}
		});
	}

	public static <T> Optional<Function<T, Publisher<T>>> flatMapObligation(Function<T, Publisher<T>> function) {
		if (function == null)
			return Optional.empty();

		return Optional.of(t -> {
			var original = function.apply(t);
			Publisher<T> result = null;
			if (original == null)
				result = Flux.error(new IllegalStateException("Handler function must not return null"));
			else
				result = original;
			return Flux.from(result).onErrorMap(
					e -> new AccessDeniedException("Error during obligation handling: " + e.getMessage(), e));
		});
	}

	public static <T> Optional<LongConsumer> obligation(LongConsumer consumer) {
		if (consumer == null)
			return Optional.empty();

		return Optional.of(t -> {
			try {
				consumer.accept(t);
			} catch (Throwable e) {
				throw new AccessDeniedException("Error during obligation handling: " + e.getMessage(), e);
			}
		});
	}

	public static <T> Optional<Runnable> obligation(Runnable runnable) {
		if (runnable == null)
			return Optional.empty();

		return Optional.of(() -> {
			try {
				runnable.run();
			} catch (Throwable e) {
				throw new AccessDeniedException("Error during obligation handling: " + e.getMessage(), e);
			}
		});
	}
}
