package io.sapl.spring.method.reactive;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class InvocationUtil {
	@SneakyThrows
	@SuppressWarnings("unchecked")
	public static <T extends Publisher<?>> T proceed(final MethodInvocation invocation) {
		return (T) invocation.proceed();
	}
}
