/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.plugins;

import io.sapl.api.functions.Function;
import io.sapl.api.plugins.FunctionInvocation;
import io.sapl.api.plugins.FunctionSpecification;
import io.sapl.api.value.Value;
import io.sapl.interpreter.InitializationException;
import lombok.val;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes annotated methods to create optimized function specifications.
 * Uses MethodHandles instead of reflection for better performance.
 */
public class MethodSignatureProcessor {

    public static FunctionSpecification functionSpecification(Object libraryInstance, String namespace, Method method)
            throws InitializationException {
        if (!method.isAnnotationPresent(Function.class)) {
            return null;
        }

        validateStaticMethodRequirement(libraryInstance, method);
        validateReturnType(method);

        val annotation    = method.getAnnotation(Function.class);
        val name          = annotationNameOrMethodName(annotation.name(), method);
        val parameterInfo = extractParameterTypes(method);

        try {
            val function = createFunctionForMethod(libraryInstance, method, parameterInfo.parameterTypes,
                    parameterInfo.varArgsParameterType);
            return new FunctionSpecification(namespace, name, parameterInfo.parameterTypes,
                    parameterInfo.varArgsParameterType, function);
        } catch (IllegalAccessException exception) {
            throw new InitializationException("Failed to create MethodHandle for function: " + name, exception);
        }
    }

    private static void validateStaticMethodRequirement(Object libraryInstance, Method method)
            throws InitializationException {
        if (libraryInstance != null) {
            return;
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            throw new InitializationException(
                    "Function method '%s' must be static when no library instance is provided."
                            .formatted(method.getName()));
        }
    }

    private static void validateReturnType(Method method) {
        if (Value.class.isAssignableFrom(method.getReturnType())) {
            return;
        }
        throw new IllegalArgumentException("Function method must return Value or a subtype, but returns: "
                + method.getReturnType().getSimpleName());
    }

    private static ParameterInfo extractParameterTypes(Method method) throws InitializationException {
        List<Class<? extends Value>> parameterTypes       = new ArrayList<>();
        Class<? extends Value>       varArgsParameterType = null;
        val                          parameters           = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            val parameterType   = parameters[i].getType();
            val isLastParameter = i == parameters.length - 1;

            if (Value.class.isAssignableFrom(parameterType)) {
                parameterTypes.add(asValueClass(parameterType));
                continue;
            }

            if (isLastParameter && parameterType.isArray()) {
                varArgsParameterType = extractVarArgsType(parameterType);
                continue;
            }

            throw new InitializationException(
                    "Functions must only have Value or its Sub-Types as parameters, but found: "
                            + parameterType.getSimpleName() + ".");
        }

        return new ParameterInfo(parameterTypes, varArgsParameterType);
    }

    private static Class<? extends Value> extractVarArgsType(Class<?> arrayType) throws InitializationException {
        val componentType = arrayType.getComponentType();

        if (!Value.class.isAssignableFrom(componentType)) {
            throw new InitializationException(
                    "Varargs array must have Value or its Sub-Types as component type, but found: "
                            + componentType.getSimpleName() + ".");
        }

        return asValueClass(componentType);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Value> asValueClass(Class<?> clazz) {
        return (Class<? extends Value>) clazz;
    }

    static java.util.function.Function<FunctionInvocation, Value> createFunctionForMethod(Object libraryInstance,
            Method method, List<Class<? extends Value>> parameterTypes, Class<? extends Value> varArgsParameterType)
            throws IllegalAccessException {

        method.setAccessible(true);

        var methodHandle = libraryInstance == null ? MethodHandles.lookup().unreflect(method)
                : MethodHandles.lookup().unreflect(method).bindTo(libraryInstance);

        // Convert varargs to fixed-arity: last parameter becomes a regular array
        if (varArgsParameterType != null) {
            methodHandle = methodHandle.asFixedArity();
        }

        val minArgCount          = parameterTypes.size();
        val hasVarArgs           = varArgsParameterType != null;
        val methodParameterCount = hasVarArgs ? minArgCount + 1 : minArgCount;

        // Pre-compute error templates
        val fixedParamErrorTemplates = buildTypeErrorTemplates(parameterTypes.toArray(new Class[0]));
        val exactArgCountError       = "Function '%%s' requires exactly %d arguments, but received %%d"
                .formatted(minArgCount);
        val minArgCountError         = "Function '%%s' requires at least %d arguments, but received %%d"
                .formatted(minArgCount);
        val varArgErrorTemplate      = hasVarArgs
                ? "Function '%%s' varargs argument %%d: expected %s but received %%s"
                        .formatted(varArgsParameterType.getSimpleName())
                : null;

        MethodHandle finalMethodHandle = methodHandle;
        return invocation -> {
            try {
                val arguments     = invocation.arguments().toArray(new Value[0]);
                val argumentCount = arguments.length;
                val functionName  = invocation.functionName();

                // Validate argument count
                if (hasVarArgs) {
                    if (argumentCount < minArgCount) {
                        return Value.error(minArgCountError.formatted(functionName, argumentCount));
                    }
                } else {
                    if (argumentCount != minArgCount) {
                        return Value.error(exactArgCountError.formatted(functionName, argumentCount));
                    }
                }

                // Prepare method parameters array
                val methodParameters = new Object[methodParameterCount];

                // Validate and prepare fixed parameters
                for (int i = 0; i < minArgCount; i++) {
                    val argument     = arguments[i];
                    val expectedType = parameterTypes.get(i);

                    if (!expectedType.isInstance(argument)) {
                        return Value.error(fixedParamErrorTemplates[i].formatted(functionName,
                                argument.getClass().getSimpleName()));
                    }

                    methodParameters[i] = argument;
                }

                // Handle varargs if present
                if (hasVarArgs) {
                    val varArgsCount = argumentCount - minArgCount;
                    val varArgsArray = java.lang.reflect.Array.newInstance(varArgsParameterType, varArgsCount);

                    for (int i = 0; i < varArgsCount; i++) {
                        val argument = arguments[minArgCount + i];

                        if (!varArgsParameterType.isInstance(argument)) {
                            return Value.error(varArgErrorTemplate.formatted(functionName, i,
                                    argument.getClass().getSimpleName()));
                        }

                        java.lang.reflect.Array.set(varArgsArray, i, argument);
                    }

                    methodParameters[minArgCount] = varArgsArray;
                }

                return (Value) finalMethodHandle.invokeWithArguments(methodParameters);

            } catch (Throwable throwable) {
                return Value.error("Function '%s' execution failed: %s".formatted(invocation.functionName(),
                        throwable.getMessage()));
            }
        };
    }

    private static String[] buildTypeErrorTemplates(Class<?>[] expectedTypes) {
        val templates = new String[expectedTypes.length];
        for (int i = 0; i < expectedTypes.length; i++) {
            templates[i] = "Function '%%s' argument %d: expected %s but received %%s".formatted(i,
                    expectedTypes[i].getSimpleName());
        }
        return templates;
    }

    private static String annotationNameOrMethodName(String nameFromAnnotation, Method method) {
        if (null != nameFromAnnotation && !nameFromAnnotation.isBlank()) {
            return nameFromAnnotation;
        }
        return method.getName();
    }

    private record ParameterInfo(
            List<Class<? extends Value>> parameterTypes,
            Class<? extends Value> varArgsParameterType) {}
}
