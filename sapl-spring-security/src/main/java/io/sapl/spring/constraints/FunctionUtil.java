package io.sapl.spring.constraints;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FunctionUtil {

	public static final <T> Consumer<T> sink() {
		return t -> {
		};
	}

	public static final LongConsumer longSink() {
		return t -> {
		};
	}

	public static final <T> Predicate<T> all() {
		return t -> true;
	}

	public static final Runnable noop() {
		return () -> {
		};
	}
}
