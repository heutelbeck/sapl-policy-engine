package io.sapl.server.ce.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Utils for handling with {@link Stream}.
 */
public final class StreamUtils {
	/**
	 * Distincts an enumeration via a custom key.
	 * 
	 * @param <T>          the generic type of the {@link Stream}
	 * @param keyExtractor the {@link Function} delegate for the key selection
	 * @return the distincted enumeration
	 */
	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}
}
