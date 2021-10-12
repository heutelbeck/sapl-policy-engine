package io.sapl.spring.constraints;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;

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

	public static Optional<LongConsumer> obligation(LongConsumer consumer) {
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

	public static Optional<Runnable> obligation(Runnable runnable) {
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
