package io.sapl.spring.constraints;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BundleUtil {
	public static <V> Consumer<V> consumeAll(List<Consumer<V>> handlers) {
		return value -> handlers.forEach(handler -> handler.accept(value));
	}

	public static Runnable runAll(List<Runnable> handlers) {
		return () -> handlers.forEach(Runnable::run);
	}

	public static <V> Function<V, V> mapAll(Collection<Function<V, V>> handlers) {
		return handlers.stream()
				.reduce(Function.identity(), (merged, newFunction) -> x -> newFunction.apply(merged.apply(x)));
	}
}
