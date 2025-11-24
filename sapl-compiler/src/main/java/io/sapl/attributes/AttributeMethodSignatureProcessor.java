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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeFinder;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.AttributeFinderSpecification;
import io.sapl.api.model.Value;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.interpreter.InitializationException;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class AttributeMethodSignatureProcessor {

    public static final String BAD_PARAMETER_TYPE_ERROR             = "Attributes must only have Value or its Sub-Types as parameters, but found: %s.";
    public static final String BAD_RETURN_TYPE_ERROR                = "Attribute method must return Flux<Value>, Mono<Value>, or their subtypes, but returns: %s.";
    public static final String BAD_VARARGS_PARAMETER_TYPE_ERROR     = "Varargs array must have Value or its Sub-Types as component type, but found: %s.";
    public static final String ATTRIBUTE_NOT_STATIC_ERROR           = "Attribute method '%s' must be static when no PIP instance is provided.";
    public static final String FAILED_TO_CREATE_METHOD_HANDLE_ERROR = "Failed to create MethodHandle for attribute: %s.";
    public static final String EXACT_ARG_COUNT_ERROR_TEMPLATE       = "Attribute '%%s' requires exactly %d arguments, but received %%d";
    public static final String MIN_ARG_COUNT_ERROR_TEMPLATE         = "Attribute '%%s' requires at least %d arguments, but received %%d";
    public static final String TYPE_ERROR_TEMPLATE                  = "Attribute '%%s' argument %d: expected %s but received %%s";
    public static final String VARARG_TYPE_ERROR_TEMPLATE           = "Attribute '%%s' varargs argument %%d: expected %s but received %%s";
    public static final String ATTRIBUTE_EXECUTION_ERROR_TEMPLATE   = "Attribute '%s' execution failed: %s";

    public static AttributeFinderSpecification processAttributeMethod(Object pipInstance, String namespace,
            Method method) throws InitializationException {
        val hasAttribute            = method.isAnnotationPresent(Attribute.class);
        val hasEnvironmentAttribute = method.isAnnotationPresent(EnvironmentAttribute.class);

        if (!hasAttribute && !hasEnvironmentAttribute) {
            return null;
        }

        validateStaticMethodRequirement(pipInstance, method);
        validateReturnType(method);

        val name          = extractAttributeName(method, hasAttribute);
        val signatureInfo = extractSignature(method, hasEnvironmentAttribute);
        val returnsFlux   = isFluxReturnType(method);

        try {
            val attributeFinder = createAttributeFinderForMethod(pipInstance, method, signatureInfo, returnsFlux);
            return new AttributeFinderSpecification(namespace, name, hasEnvironmentAttribute,
                    signatureInfo.parameterTypes, signatureInfo.varArgsParameterType, attributeFinder);
        } catch (IllegalAccessException exception) {
            throw new InitializationException(FAILED_TO_CREATE_METHOD_HANDLE_ERROR.formatted(name), exception);
        }
    }

    private static String extractAttributeName(Method method, boolean hasAttribute) {
        if (hasAttribute) {
            val annotation = method.getAnnotation(Attribute.class);
            return annotationNameOrMethodName(annotation.name(), method);
        }
        val annotation = method.getAnnotation(EnvironmentAttribute.class);
        return annotationNameOrMethodName(annotation.name(), method);
    }

    private static void validateStaticMethodRequirement(Object pipInstance, Method method)
            throws InitializationException {
        if (pipInstance == null && !Modifier.isStatic(method.getModifiers())) {
            throw new InitializationException(ATTRIBUTE_NOT_STATIC_ERROR.formatted(method.getName()));
        }
    }

    private static void validateReturnType(Method method) throws InitializationException {
        val returnType = method.getGenericReturnType();

        if (!(returnType instanceof ParameterizedType paramType)) {
            throw new InitializationException(BAD_RETURN_TYPE_ERROR.formatted(returnType.getTypeName()));
        }

        val rawType = paramType.getRawType();
        if (!Flux.class.equals(rawType) && !Mono.class.equals(rawType)) {
            throw new InitializationException(BAD_RETURN_TYPE_ERROR.formatted(returnType.getTypeName()));
        }

        val typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length != 1) {
            throw new InitializationException(BAD_RETURN_TYPE_ERROR.formatted(returnType.getTypeName()));
        }

        val elementType = typeArgs[0];
        if (elementType instanceof Class<?> clazz && !Value.class.isAssignableFrom(clazz)) {
            throw new InitializationException(BAD_RETURN_TYPE_ERROR.formatted(returnType.getTypeName()));
        }
    }

    private static boolean isFluxReturnType(Method method) {
        val returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType paramType) {
            return Flux.class.equals(paramType.getRawType());
        }
        return false;
    }

    private static SignatureInfo extractSignature(Method method, boolean isEnvironmentAttribute)
            throws InitializationException {
        val parameters = method.getParameters();
        int startIndex = determineStartIndex(parameters, isEnvironmentAttribute);

        if (startIndex < parameters.length && parameters[startIndex].getType().equals(Map.class)) {
            startIndex++;
        }

        List<Class<? extends Value>> parameterTypes       = new ArrayList<>();
        Class<? extends Value>       varArgsParameterType = null;

        for (int i = startIndex; i < parameters.length; i++) {
            val parameterType   = parameters[i].getType();
            val isLastParameter = i == parameters.length - 1;

            if (Value.class.isAssignableFrom(parameterType)) {
                parameterTypes.add(asValueClass(parameterType));
            } else if (isLastParameter && parameterType.isArray()) {
                varArgsParameterType = extractVarArgsType(parameterType);
            } else {
                throw new InitializationException(BAD_PARAMETER_TYPE_ERROR.formatted(parameterType.getSimpleName()));
            }
        }

        return new SignatureInfo(parameterTypes, varArgsParameterType);
    }

    private static int determineStartIndex(java.lang.reflect.Parameter[] parameters, boolean isEnvironmentAttribute)
            throws InitializationException {
        if (isEnvironmentAttribute || parameters.length == 0) {
            return 0;
        }

        val firstParamType = parameters[0].getType();
        if (Value.class.isAssignableFrom(firstParamType)) {
            return 1;
        }

        throw new InitializationException(BAD_PARAMETER_TYPE_ERROR.formatted(firstParamType.getSimpleName()));
    }

    private static Class<? extends Value> extractVarArgsType(Class<?> arrayType) throws InitializationException {
        val componentType = arrayType.getComponentType();

        if (!Value.class.isAssignableFrom(componentType)) {
            throw new InitializationException(
                    BAD_VARARGS_PARAMETER_TYPE_ERROR.formatted(componentType.getSimpleName()));
        }

        return asValueClass(componentType);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Value> asValueClass(Class<?> clazz) {
        return (Class<? extends Value>) clazz;
    }

    static AttributeFinder createAttributeFinderForMethod(Object pipInstance, Method method,
            SignatureInfo signatureInfo, boolean returnsFlux) throws IllegalAccessException {

        val methodHandle     = prepareMethodHandle(pipInstance, method, signatureInfo);
        val invocationConfig = new InvocationConfig(method, signatureInfo, returnsFlux);

        return invocation -> Flux.deferContextual(ctx -> {
            try {
                val arguments = invocation.arguments().toArray(new Value[0]);

                val validationError = validateArgumentCount(invocationConfig, invocation.attributeName(),
                        arguments.length);
                if (validationError != null) {
                    return Flux.just(validationError);
                }

                val methodParameters = buildMethodParameters(invocationConfig, invocation, arguments);
                if (methodParameters instanceof Value errorValue) {
                    return Flux.just(errorValue);
                }

                val result = methodHandle.invokeWithArguments((Object[]) methodParameters);
                return convertResultToFlux(result, returnsFlux);

            } catch (Throwable throwable) {
                return Flux.just(Value.error(ATTRIBUTE_EXECUTION_ERROR_TEMPLATE, invocation.attributeName(),
                        throwable.getMessage()));
            }
        });
    }

    private static MethodHandle prepareMethodHandle(Object pipInstance, Method method, SignatureInfo signatureInfo)
            throws IllegalAccessException {
        val isStaticMethod = Modifier.isStatic(method.getModifiers());

        var methodHandle = MethodHandles.lookup().unreflect(method);

        if (!isStaticMethod && pipInstance != null) {
            methodHandle = methodHandle.bindTo(pipInstance);
        }

        if (signatureInfo.varArgsParameterType != null) {
            methodHandle = methodHandle.asFixedArity();
        }

        return methodHandle;
    }

    private static Value validateArgumentCount(InvocationConfig config, String attributeName, int argumentCount) {
        if (config.hasVarArgs) {
            if (argumentCount < config.minArgCount) {
                return Value.error(config.minArgCountError.formatted(attributeName, argumentCount));
            }
        } else {
            if (argumentCount != config.minArgCount) {
                return Value.error(config.exactArgCountError.formatted(attributeName, argumentCount));
            }
        }
        return null;
    }

    private static Object buildMethodParameters(InvocationConfig config, AttributeFinderInvocation invocation,
            Value[] arguments) {
        val methodParameters = new Object[config.methodParameterCount];
        int paramIndex       = 0;

        paramIndex = addContextParameters(methodParameters, paramIndex, config, invocation);

        val fixedParamsResult = addFixedParameters(methodParameters, paramIndex, config, invocation.attributeName(),
                arguments);
        if (fixedParamsResult instanceof Value errorValue) {
            return errorValue;
        }
        paramIndex = (Integer) fixedParamsResult;

        if (config.hasVarArgs) {
            val varArgsResult = addVarArgsParameters(methodParameters, paramIndex, config, invocation.attributeName(),
                    arguments);
            if (varArgsResult instanceof Value errorValue) {
                return errorValue;
            }
        }

        return methodParameters;
    }

    private static int addContextParameters(Object[] methodParameters, int paramIndex, InvocationConfig config,
            AttributeFinderInvocation invocation) {
        if (config.hasEntityParam) {
            methodParameters[paramIndex++] = invocation.entity();
        }
        if (config.hasVariablesParam) {
            methodParameters[paramIndex++] = invocation.variables();
        }
        return paramIndex;
    }

    private static Object addFixedParameters(Object[] methodParameters, int startIndex, InvocationConfig config,
            String attributeName, Value[] arguments) {
        int paramIndex = startIndex;
        for (int i = 0; i < config.minArgCount; i++) {
            val argument     = arguments[i];
            val expectedType = config.parameterTypes.get(i);

            if (!expectedType.isInstance(argument)) {
                return Value.error(config.fixedParamErrorTemplates[i].formatted(attributeName,
                        argument.getClass().getSimpleName()));
            }

            methodParameters[paramIndex++] = argument;
        }
        return paramIndex;
    }

    private static Object addVarArgsParameters(Object[] methodParameters, int paramIndex, InvocationConfig config,
            String attributeName, Value[] arguments) {
        val varArgsCount = arguments.length - config.minArgCount;
        val varArgsArray = Array.newInstance(config.varArgsParameterType, varArgsCount);

        for (int i = 0; i < varArgsCount; i++) {
            val argument = arguments[config.minArgCount + i];

            if (!config.varArgsParameterType.isInstance(argument)) {
                return Value.error(
                        config.varArgErrorTemplate.formatted(attributeName, i, argument.getClass().getSimpleName()));
            }

            Array.set(varArgsArray, i, argument);
        }

        methodParameters[paramIndex] = varArgsArray;
        return paramIndex;
    }

    @SuppressWarnings("unchecked")
    private static Flux<Value> convertResultToFlux(Object result, boolean returnsFlux) {
        if (returnsFlux) {
            return (Flux<Value>) result;
        }
        return ((Mono<Value>) result).flux();
    }

    private static int calculateMethodParameterCount(Method method, boolean hasVarArgs, int minArgCount) {
        int count = 0;
        if (hasEntityParameter(method)) {
            count++;
        }
        if (hasVariablesParameter(method)) {
            count++;
        }
        count += hasVarArgs ? minArgCount + 1 : minArgCount;
        return count;
    }

    private static boolean hasEntityParameter(Method method) {
        if (method.isAnnotationPresent(EnvironmentAttribute.class)) {
            return false;
        }
        val parameters = method.getParameters();
        if (parameters.length == 0) {
            return false;
        }
        return Value.class.isAssignableFrom(parameters[0].getType());
    }

    private static boolean hasVariablesParameter(Method method) {
        val parameters     = method.getParameters();
        val hasEntityParam = hasEntityParameter(method);
        val checkIndex     = hasEntityParam ? 1 : 0;

        if (parameters.length <= checkIndex) {
            return false;
        }

        return Map.class.equals(parameters[checkIndex].getType());
    }

    private static String[] buildTypeErrorTemplates(Class<?>[] expectedTypes) {
        val templates = new String[expectedTypes.length];
        for (int i = 0; i < expectedTypes.length; i++) {
            templates[i] = TYPE_ERROR_TEMPLATE.formatted(i, expectedTypes[i].getSimpleName());
        }
        return templates;
    }

    private static String annotationNameOrMethodName(String nameFromAnnotation, Method method) {
        if (null != nameFromAnnotation && !nameFromAnnotation.isBlank()) {
            return nameFromAnnotation;
        }
        return method.getName();
    }

    private record SignatureInfo(
            List<Class<? extends Value>> parameterTypes,
            Class<? extends Value> varArgsParameterType) {}

    private record InvocationConfig(
            int minArgCount,
            boolean hasVarArgs,
            boolean hasEntityParam,
            boolean hasVariablesParam,
            int methodParameterCount,
            List<Class<? extends Value>> parameterTypes,
            Class<? extends Value> varArgsParameterType,
            String[] fixedParamErrorTemplates,
            String exactArgCountError,
            String minArgCountError,
            String varArgErrorTemplate) {

        InvocationConfig(Method method, SignatureInfo signatureInfo, boolean returnsFlux) {
            this(signatureInfo.parameterTypes.size(), signatureInfo.varArgsParameterType != null,
                    hasEntityParameter(method), hasVariablesParameter(method),
                    calculateMethodParameterCount(method, signatureInfo.varArgsParameterType != null,
                            signatureInfo.parameterTypes.size()),
                    signatureInfo.parameterTypes, signatureInfo.varArgsParameterType,
                    buildTypeErrorTemplates(signatureInfo.parameterTypes.toArray(new Class[0])),
                    EXACT_ARG_COUNT_ERROR_TEMPLATE.formatted(signatureInfo.parameterTypes.size()),
                    MIN_ARG_COUNT_ERROR_TEMPLATE.formatted(signatureInfo.parameterTypes.size()),
                    signatureInfo.varArgsParameterType != null
                            ? VARARG_TYPE_ERROR_TEMPLATE.formatted(signatureInfo.varArgsParameterType.getSimpleName())
                            : null);
        }
    }

}
