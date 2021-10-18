package io.sapl.spring.method.reactive;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInvocation;

public class MockMethodInvocation implements MethodInvocation {

	private Method method;

	private Object[] arguments;

	private Object targetObject;

	private Supplier<Object> proceedSupplier;
	
	public static MockMethodInvocation of(Object targetObject, Class<?> clazz, String methodName,
			Supplier<Object> proceedSupplier, Class<?>[] classArgs, Object[] args) {
		try {
			Method method = clazz.getMethod(methodName, classArgs);
			return new MockMethodInvocation(targetObject, method, proceedSupplier, args);
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	public MockMethodInvocation(Object targetObject, Method method, Supplier<Object> proceedSupplier,
			Object... arguments) {
		this.targetObject = targetObject;
		this.method = method;
		this.arguments = (arguments != null) ? arguments : new Object[0];
		this.proceedSupplier = proceedSupplier;
	}

	public MockMethodInvocation() {
	}

	@Override
	public Object[] getArguments() {
		return this.arguments;
	}

	@Override
	public Method getMethod() {
		return this.method;
	}

	@Override
	public AccessibleObject getStaticPart() {
		throw new UnsupportedOperationException("mock method not implemented");
	}

	@Override
	public Object getThis() {
		return this.targetObject;
	}

	@Override
	public Object proceed() {
		return proceedSupplier.get();
	}

	@Override
	public String toString() {
		return "method invocation [" + this.method + "]";
	}

	

}
