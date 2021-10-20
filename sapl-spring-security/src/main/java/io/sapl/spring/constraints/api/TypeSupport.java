package io.sapl.spring.constraints.api;

public interface TypeSupport<T> {
	Class<T> getSupportedType();

	default boolean supports(Class<?> clazz) {
		return getSupportedType().isAssignableFrom(clazz);
	}
}