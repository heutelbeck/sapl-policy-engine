/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinder;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.AttributeFinderSpecification;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class AttributeMethodSignatureProcessor {

    public static final String ERROR_ATTRIBUTE_EXECUTION_TEMPLATE   = "Attribute '%s' execution failed: %s";
    public static final String ERROR_ATTRIBUTE_NOT_STATIC           = "Attribute method '%s' must be static when no PIP instance is provided.";
    public static final String ERROR_BAD_PARAMETER_TYPE             = "Attributes must only have Value or its Sub-Types as parameters, but found: %s.";
    public static final String ERROR_BAD_RETURN_TYPE                = "Attribute method must return Flux<Value>, Mono<Value>, or their subtypes, but returns: %s.";
    public static final String ERROR_BAD_VARARGS_PARAMETER_TYPE     = "Varargs array must have Value or its Sub-Types as component type, but found: %s.";
    public static final String ERROR_EXACT_ARG_COUNT_TEMPLATE       = "Attribute '%%s' requires exactly %d arguments, but received %%d";
    public static final String ERROR_FAILED_TO_CREATE_METHOD_HANDLE = "Failed to create MethodHandle for attribute: %s.";
    public static final String ERROR_MIN_ARG_COUNT_TEMPLATE         = "Attribute '%%s' requires at least %d arguments, but received %%d";
    public static final String ERROR_TYPE_TEMPLATE                  = "Attribute '%%s' argument %d: expected %s but received %%s";
    public static final String ERROR_VARARG_TYPE_TEMPLATE           = "Attribute '%%s' varargs argument %%d: expected %s but received %%s";

    /**
     * Processes a method to create an attribute finder specification.
     *
     * @param pipInstance
     * the PIP instance to bind instance methods to, or null for static
     * methods
     * @param namespace
     * the namespace prefix for the attribute
     * @param method
     * the method annotated with {@link Attribute} or
     * {@link EnvironmentAttribute}
     *
     * @return an AttributeFinderSpecification for the method, or null if not
     * annotated
     *
     * @throws IllegalStateException
     * if the method is invalid (non-static without instance, bad
     * parameter/return types)
     */
    public static AttributeFinderSpecification processAttributeMethod(Object pipInstance, String namespace,
            Method method) {
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
            throw new IllegalStateException(ERROR_FAILED_TO_CREATE_METHOD_HANDLE.formatted(name), exception);
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

    private static void validateStaticMethodRequirement(Object pipInstance, Method method) {
        if (pipInstance == null && !Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException(ERROR_ATTRIBUTE_NOT_STATIC.formatted(method.getName()));
        }
    }

    private static void validateReturnType(Method method) {
        val returnType = method.getGenericReturnType();

        if (!(returnType instanceof ParameterizedType paramType)) {
            throw new IllegalStateException(ERROR_BAD_RETURN_TYPE.formatted(returnType.getTypeName()));
        }

        val rawType = paramType.getRawType();
        if (!Flux.class.equals(rawType) && !Mono.class.equals(rawType)) {
            throw new IllegalStateException(ERROR_BAD_RETURN_TYPE.formatted(returnType.getTypeName()));
        }

        val typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length != 1) {
            throw new IllegalStateException(ERROR_BAD_RETURN_TYPE.formatted(returnType.getTypeName()));
        }

        val elementType = typeArgs[0];
        if (elementType instanceof Class<?> clazz && !Value.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException(ERROR_BAD_RETURN_TYPE.formatted(returnType.getTypeName()));
        }
    }

    private static boolean isFluxReturnType(Method method) {
        val returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType paramType) {
            return Flux.class.equals(paramType.getRawType());
        }
        return false;
    }

    private static SignatureInfo extractSignature(Method method, boolean isEnvironmentAttribute) {
        val parameters = method.getParameters();
        var startIndex = determineStartIndex(parameters, isEnvironmentAttribute);

        if (startIndex < parameters.length && parameters[startIndex].getType().equals(AttributeAccessContext.class)) {
            startIndex++;
        }

        val                    parameterTypes       = new ArrayList<Class<? extends Value>>();
        Class<? extends Value> varArgsParameterType = null;

        for (int i = startIndex; i < parameters.length; i++) {
            val parameterType   = parameters[i].getType();
            val isLastParameter = i == parameters.length - 1;

            if (Value.class.isAssignableFrom(parameterType)) {
                parameterTypes.add(asValueClass(parameterType));
            } else if (isLastParameter && parameterType.isArray()) {
                varArgsParameterType = extractVarArgsType(parameterType);
            } else {
                throw new IllegalStateException(ERROR_BAD_PARAMETER_TYPE.formatted(parameterType.getSimpleName()));
            }
        }

        return new SignatureInfo(parameterTypes, varArgsParameterType);
    }

    private static int determineStartIndex(Parameter[] parameters, boolean isEnvironmentAttribute) {
        if (isEnvironmentAttribute || parameters.length == 0) {
            return 0;
        }

        val firstParamType = parameters[0].getType();
        if (Value.class.isAssignableFrom(firstParamType)) {
            return 1;
        }

        throw new IllegalStateException(ERROR_BAD_PARAMETER_TYPE.formatted(firstParamType.getSimpleName()));
    }

    private static Class<? extends Value> extractVarArgsType(Class<?> arrayType) {
        val componentType = arrayType.getComponentType();

        if (!Value.class.isAssignableFrom(componentType)) {
            throw new IllegalStateException(ERROR_BAD_VARARGS_PARAMETER_TYPE.formatted(componentType.getSimpleName()));
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
        val invocationConfig = new InvocationConfig(method, signatureInfo);

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
                return Flux.just(Value.error(ERROR_ATTRIBUTE_EXECUTION_TEMPLATE, invocation.attributeName(),
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
        if (config.hasAttributeAccessContextParam) {
            methodParameters[paramIndex++] = invocation.ctx();
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
        if (hasAttributeAccessContextParameter(method)) {
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

    private static boolean hasAttributeAccessContextParameter(Method method) {
        val parameters     = method.getParameters();
        val hasEntityParam = hasEntityParameter(method);
        val checkIndex     = hasEntityParam ? 1 : 0;

        if (parameters.length <= checkIndex) {
            return false;
        }

        return AttributeAccessContext.class.equals(parameters[checkIndex].getType());
    }

    private static String[] buildTypeErrorTemplates(Class<?>[] expectedTypes) {
        val templates = new String[expectedTypes.length];
        for (int i = 0; i < expectedTypes.length; i++) {
            templates[i] = ERROR_TYPE_TEMPLATE.formatted(i, expectedTypes[i].getSimpleName());
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
            boolean hasAttributeAccessContextParam,
            int methodParameterCount,
            List<Class<? extends Value>> parameterTypes,
            Class<? extends Value> varArgsParameterType,
            String[] fixedParamErrorTemplates,
            String exactArgCountError,
            String minArgCountError,
            String varArgErrorTemplate) {

        InvocationConfig(Method method, SignatureInfo signatureInfo) {
            this(signatureInfo.parameterTypes.size(), signatureInfo.varArgsParameterType != null,
                    hasEntityParameter(method), hasAttributeAccessContextParameter(method),
                    calculateMethodParameterCount(method, signatureInfo.varArgsParameterType != null,
                            signatureInfo.parameterTypes.size()),
                    signatureInfo.parameterTypes, signatureInfo.varArgsParameterType,
                    buildTypeErrorTemplates(signatureInfo.parameterTypes.toArray(new Class[0])),
                    ERROR_EXACT_ARG_COUNT_TEMPLATE.formatted(signatureInfo.parameterTypes.size()),
                    ERROR_MIN_ARG_COUNT_TEMPLATE.formatted(signatureInfo.parameterTypes.size()),
                    signatureInfo.varArgsParameterType != null
                            ? ERROR_VARARG_TYPE_TEMPLATE.formatted(signatureInfo.varArgsParameterType.getSimpleName())
                            : null);
        }
    }

}
