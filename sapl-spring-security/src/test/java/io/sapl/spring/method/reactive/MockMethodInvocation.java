/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.method.reactive;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInvocation;

import lombok.NonNull;

class MockMethodInvocation implements MethodInvocation {

	private Method method;

	private Object[] arguments;

	private Object targetObject;

	private Supplier<Object> proceedSupplier;

	public static MockMethodInvocation of(Object targetObject, Class<?> clazz, String methodName,
			Supplier<Object> proceedSupplier, Class<?>[] classArgs, Object[] args) {
		try {
			Method method = clazz.getMethod(methodName, classArgs);
			return new MockMethodInvocation(targetObject, method, proceedSupplier, args);
		}
		catch (NoSuchMethodException ex) {
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
	public Object @NonNull [] getArguments() {
		return this.arguments;
	}

	@Override
	public @NonNull Method getMethod() {
		return this.method;
	}

	@Override
	public @NonNull AccessibleObject getStaticPart() {
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
