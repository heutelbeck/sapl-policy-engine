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
import io.sapl.api.model.EvaluationContext;
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
import java.util.function.Function;

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
    public static final String VARIABLES_PARAM_MUST_BE_MAP_ERROR    = "Variables parameter must be Map<String, Value> but found: %s";

    public static AttributeFinderResult processAttributeMethod(Object pipInstance, String namespace, Method method)
            throws InitializationException {
        if (!method.isAnnotationPresent(Attribute.class)) {
            return null;
        }

        validateStaticMethodRequirement(pipInstance, method);
        validateReturnType(method);

        val annotation        = method.getAnnotation(Attribute.class);
        val name              = annotationNameOrMethodName(annotation.name(), method);
        val isEnvironmentAttr = method.isAnnotationPresent(EnvironmentAttribute.class);
        val signatureInfo     = extractSignature(method, isEnvironmentAttr);
        val returnsFlux       = isFluxReturnType(method);

        try {
            val attributeFinder = createAttributeFinderForMethod(pipInstance, method, signatureInfo, returnsFlux);
            return new AttributeFinderResult(namespace, name, isEnvironmentAttr, signatureInfo.parameterTypes,
                    signatureInfo.varArgsParameterType, attributeFinder);
        } catch (IllegalAccessException exception) {
            throw new InitializationException(FAILED_TO_CREATE_METHOD_HANDLE_ERROR.formatted(name), exception);
        }
    }

    private static void validateStaticMethodRequirement(Object pipInstance, Method method)
            throws InitializationException {
        if (pipInstance != null) {
            return;
        }

        if (!Modifier.isStatic(method.getModifiers())) {
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
        int startIndex = 0;

        if (!isEnvironmentAttribute && parameters.length > 0) {
            val firstParamType = parameters[0].getType();
            if (Value.class.isAssignableFrom(firstParamType)) {
                startIndex = 1;
            } else {
                throw new InitializationException(BAD_PARAMETER_TYPE_ERROR.formatted(firstParamType.getSimpleName()));
            }
        }

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
                continue;
            }

            if (isLastParameter && parameterType.isArray()) {
                varArgsParameterType = extractVarArgsType(parameterType);
                continue;
            }

            throw new InitializationException(BAD_PARAMETER_TYPE_ERROR.formatted(parameterType.getSimpleName()));
        }

        return new SignatureInfo(parameterTypes, varArgsParameterType);
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

    static AttributeFinder createAttributeFinderForMethod(
            Object pipInstance, Method method, SignatureInfo signatureInfo, boolean returnsFlux)
            throws IllegalAccessException {

        method.setAccessible(true);

        val isStaticMethod = Modifier.isStatic(method.getModifiers());

        var methodHandle = MethodHandles.lookup().unreflect(method);

        if (!isStaticMethod && pipInstance != null) {
            methodHandle = methodHandle.bindTo(pipInstance);
        }

        if (signatureInfo.varArgsParameterType != null) {
            methodHandle = methodHandle.asFixedArity();
        }

        val minArgCount          = signatureInfo.parameterTypes.size();
        val hasVarArgs           = signatureInfo.varArgsParameterType != null;
        val methodParameterCount = calculateMethodParameterCount(method, hasVarArgs, minArgCount);

        val fixedParamErrorTemplates = buildTypeErrorTemplates(signatureInfo.parameterTypes.toArray(new Class[0]));
        val exactArgCountError       = EXACT_ARG_COUNT_ERROR_TEMPLATE.formatted(minArgCount);
        val minArgCountError         = MIN_ARG_COUNT_ERROR_TEMPLATE.formatted(minArgCount);
        val varArgErrorTemplate      = hasVarArgs
                ? VARARG_TYPE_ERROR_TEMPLATE.formatted(signatureInfo.varArgsParameterType.getSimpleName())
                : null;

        val hasEntityParam    = hasEntityParameter(method);
        val hasVariablesParam = hasVariablesParameter(method);

        MethodHandle finalMethodHandle = methodHandle;
        return invocation -> Flux.deferContextual(ctx -> {
            try {
                val evaluationContext = ctx.get(EvaluationContext.class);
                val variables         = evaluationContext.variables();
                val arguments         = invocation.arguments().toArray(new Value[0]);
                val argumentCount     = arguments.length;
                val attributeName     = invocation.attributeName();

                if (hasVarArgs) {
                    if (argumentCount < minArgCount) {
                        return Flux.just(Value.error(minArgCountError.formatted(attributeName, argumentCount)));
                    }
                } else {
                    if (argumentCount != minArgCount) {
                        return Flux.just(Value.error(exactArgCountError.formatted(attributeName, argumentCount)));
                    }
                }

                val methodParameters = new Object[methodParameterCount];
                int paramIndex       = 0;

                if (hasEntityParam) {
                    methodParameters[paramIndex++] = invocation.entity();
                }

                if (hasVariablesParam) {
                    methodParameters[paramIndex++] = variables;
                }

                for (int i = 0; i < minArgCount; i++) {
                    val argument     = arguments[i];
                    val expectedType = signatureInfo.parameterTypes.get(i);

                    if (!expectedType.isInstance(argument)) {
                        return Flux.just(Value.error(fixedParamErrorTemplates[i].formatted(attributeName,
                                argument.getClass().getSimpleName())));
                    }

                    methodParameters[paramIndex++] = argument;
                }

                if (hasVarArgs) {
                    val varArgsCount = argumentCount - minArgCount;
                    val varArgsArray = Array.newInstance(signatureInfo.varArgsParameterType,
                            varArgsCount);

                    for (int i = 0; i < varArgsCount; i++) {
                        val argument = arguments[minArgCount + i];

                        if (!signatureInfo.varArgsParameterType.isInstance(argument)) {
                            return Flux.just(Value.error(varArgErrorTemplate.formatted(attributeName, i,
                                    argument.getClass().getSimpleName())));
                        }

                        Array.set(varArgsArray, i, argument);
                    }

                    methodParameters[paramIndex] = varArgsArray;
                }

                val result = finalMethodHandle.invokeWithArguments(methodParameters);

                if (returnsFlux) {
                    return (Flux<Value>) result;
                } else {
                    return ((Mono<Value>) result).flux();
                }

            } catch (Throwable throwable) {
                return Flux.just(Value.error(ATTRIBUTE_EXECUTION_ERROR_TEMPLATE.formatted(invocation.attributeName(),
                        throwable.getMessage())));
            }
        });
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

    public record AttributeFinderResult(
            String namespace,
            String attributeName,
            boolean isEnvironmentAttribute,
            List<Class<? extends Value>> parameterTypes,
            Class<? extends Value> varArgsParameterType,
            AttributeFinder attributeFinder) {}
}
