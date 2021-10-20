package io.sapl.spring.method.reactive;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InvocationUtil {
	@SneakyThrows
	@SuppressWarnings("unchecked")
	public static <T> Publisher<T> proceed(final MethodInvocation invocation) {
		return (Publisher<T>) invocation.proceed();
	}
}
