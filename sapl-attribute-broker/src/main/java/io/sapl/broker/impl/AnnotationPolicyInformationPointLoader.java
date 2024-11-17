/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.broker.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import io.sapl.api.broker.AttributeStreamBroker;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CorePublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class AnnotationPolicyInformationPointLoader {

    static final String A_PIP_WITH_THE_NAME_S_ALREADY_REGISTERED_ERROR = """
            A PIP with the name '%s' has already been registered.""";

    static final String CLASS_BASED_PIP_NEEDS_STATIC_METHOD_S_ERROR = """
            Cannot initialize PIPs. If no PIP instance is provided, the method of an attribute finder must be static. \
            %s is not static. In case your PIP implementation cannot have the method as static because it \
            depends on PIP state or injected dependencies, make sure to register the PIP as an instance \
            instead of a class.""";

    static final String FIRST_PARAMETER_NOT_PRESENT_S_ERROR = """
            Argument missing. First parameter of the method '%s' must be a Val for taking in the left-hand argument, \
            but no argument was present.""";

    static final String FIRST_PARAMETER_S_UNEXPECTED_TYPE_S_ERROR = """
            First parameter of the method %s has an unexpected type. Was expecting a Val but got '%s'.""";

    static final String INVALID_SCHEMA_DEFINITION_ERROR = """
            Invalid schema definition for attribute found. This only validated JSON syntax, not \
            compliance with the JSONSchema specification""";

    static final String MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED_ERROR = """
            Attribute has both a schema and a schemaPath annotation. Multiple schema annotations are not allowed.""";

    static final String NO_POLICY_INFORMATION_POINT_ANNOTATION_ERROR = """
            Provided class has no @PolicyInformationPoint annotation.""";

    static final String NON_VAL_PARAMETER_AT_METHOD_S_ERROR = "The method '%s' declared a non Val as a parameter.";

    static final String PIP_S_DECLARES_NO_ATTRIBUTES_ERROR = """
            The PIP with the name '%s' does not declare any attributes. To declare an attribute, annotate a \
            method with @Attribute or @EnvironmentAttribute.""";

    static final String RETURN_TYPE_MUST_BE_FLUX_OR_MONO_OF_VALUES_ERROR = """
            The return type of an attribute finder must be Flux<Val> or Mono<Val>. Was: %s<%s>""";

    static final String UNKNOWN_ATTRIBUTE_ERROR = "Unknown attribute '%s'.";

    static final String VARARGS_MISMATCH_AT_METHOD_S_ERROR = """
            The method '%s' has an array of Val as a parameter, which indicates a variable number of arguments. \
            However the array is followed by some other parameters. This is prohibited. \
            The array must be the last parameter of the attribute declaration.""";

    private final AttributeStreamBroker broker;

    /**
     * Initialize with context from a supplied PIPs.
     *
     * @param pipSupplier supplies instantiated libraries
     * @param staticPipSupplier supplies libraries contained in utility classes with
     * static methods as functions
     */
    public AnnotationPolicyInformationPointLoader(AttributeStreamBroker broker,
            PolicyInformationPointSupplier pipSupplier, StaticPolicyInformationPointSupplier staticPipSupplier) {
        this.broker = broker;
        loadPolicyInformationPoints(pipSupplier);
        loadPolicyInformationPoints(staticPipSupplier);
    }

    /**
     * Loads supplied policy information point instances.
     *
     * @param staticPipSupplier supplies libraries contained in utility classes with
     * static methods as functions
     */
    public void loadPolicyInformationPoints(StaticPolicyInformationPointSupplier staticPipSupplier) {
        for (final var pip : staticPipSupplier.get()) {
            loadPolicyInformationPoint(pip);
        }
    }

    /**
     * Loads supplied static policy information points.
     *
     * @param pipSupplier supplies instantiated libraries.
     */
    public void loadPolicyInformationPoints(PolicyInformationPointSupplier pipSupplier) {
        for (final var pip : pipSupplier.get()) {
            loadPolicyInformationPoint(pip);
        }
    }

    /**
     * @param policyInformationPoint an instance of a Policy Information Point.
     */
    public void loadPolicyInformationPoint(Object policyInformationPoint) {
        loadPolicyInformationPoint(policyInformationPoint, policyInformationPoint.getClass());
    }

    /**
     * @param policyInformationPointClass the Class of a Policy information Point
     * supplying static method Attribute finder methods only.
     */
    public void loadStaticPolicyInformationPoint(Class<?> policyInformationPointClass) {
        loadPolicyInformationPoint(null, policyInformationPointClass);
    }

    private void loadPolicyInformationPoint(Object policyInformationPoint, Class<?> pipClass) {
        final var pipAnnotation = pipClass.getAnnotation(PolicyInformationPoint.class);

        if (null == pipAnnotation) {
            throw new AttributeBrokerException(NO_POLICY_INFORMATION_POINT_ANNOTATION_ERROR);
        }

        var pipName = pipAnnotation.name();
        if (pipName.isBlank()) {
            pipName = pipClass.getSimpleName();
        }

        var foundAtLeastOneSuppliedAttributeInPip = false;
        for (final Method method : pipClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Attribute.class)) {
                foundAtLeastOneSuppliedAttributeInPip = true;
                final var annotation                  = method.getAnnotation(Attribute.class);
                final var fullyQualifiedAttributeName = fullyQualifiedAttributeName(pipClass, method, pipName,
                        annotation.name());
                final var specification               = getSpecificationOfAttribute(policyInformationPoint,
                        fullyQualifiedAttributeName, method, false);
                final var attributeFinder             = createAttributeFinder(policyInformationPoint, method,
                        annotation, specification);
                log.debug("Found Attribute: {}", specification);
            }
            if (method.isAnnotationPresent(EnvironmentAttribute.class)) {
                foundAtLeastOneSuppliedAttributeInPip = true;
                final var annotation                  = method.getAnnotation(EnvironmentAttribute.class);
                final var fullyQualifiedAttributeName = fullyQualifiedAttributeName(pipClass, method, pipName,
                        annotation.name());
                final var specification               = getSpecificationOfAttribute(policyInformationPoint,
                        fullyQualifiedAttributeName, method, true);
                final var attributeFinder             = createEnvironmentAttributeFinder(policyInformationPoint, method,
                        annotation, specification);
                log.debug("Found EnvironmentAttribute: {}", specification);
            }
        }

        if (!foundAtLeastOneSuppliedAttributeInPip) {
            throw new AttributeBrokerException(String.format(PIP_S_DECLARES_NO_ATTRIBUTES_ERROR, pipName));
        }
    }

    private AttributeFinder createEnvironmentAttributeFinder(Object policyInformationPoint, Method method,
            EnvironmentAttribute annotation, AttributeSpecification specification) {
        return null;
    }

    private AttributeFinder createAttributeFinder(Object policyInformationPoint, Method method, Attribute annotation,
            AttributeSpecification specification) {
        assertValidReturnType(method);
        final var      methodReturnsMono = methodReturnsMono(method);
        final Object[] arguments         = new Object[0];
        return invocation -> {
            try {
                return (Flux<Val>) method.invoke(policyInformationPoint, arguments);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                return Flux.error(e);
            }
        };
    }

    private static Object[] attributeFinderArguments(AttributeSpecification attributeMetadata,
            PolicyInformationPointInvocation invocation) {
        final var numberOfInvocationParameters = numberOfInvocationParametersForAttribute(attributeMetadata,
                invocation);
        final var invocationArguments          = new Object[numberOfInvocationParameters];

        var argumentIndex = 0;
        if (!attributeMetadata.isEnvironmentAttribute()) {
            invocationArguments[argumentIndex++] = invocation.entity();
        }

        if (attributeMetadata.takesVariables()) {
            invocationArguments[argumentIndex++] = invocation.variables();
        }

        if (attributeMetadata.hasVariableNumberOfArguments()) {
            final var varArgsArgument = new Val[invocation.arguments().size()];
            var       i               = 0;
            for (var arg : invocation.arguments()) {
                varArgsArgument[i++] = arg;
            }
            invocationArguments[argumentIndex] = varArgsArgument;
        } else {
            for (var arg : invocation.arguments()) {
                invocationArguments[argumentIndex++] = arg;
            }
        }
        return invocationArguments;
    }

    private static int numberOfInvocationParametersForAttribute(AttributeSpecification attributeMetadata,
            PolicyInformationPointInvocation invocation) {

        var numberOfArguments = 0;

        if (!attributeMetadata.isEnvironmentAttribute()) {
            numberOfArguments++;
        }

        if (attributeMetadata.takesVariables()) {
            numberOfArguments++;
        }

        if (attributeMetadata.hasVariableNumberOfArguments()) {
            numberOfArguments++;
        } else {
            numberOfArguments += invocation.arguments().size();
        }

        return numberOfArguments;
    }

    private static AttributeSpecification getSpecificationOfAttribute(Object policyInformationPoint,
            String fullyQualifiedAttributeName, Method method, boolean isEnvironmentAttribute) {
        if (null == policyInformationPoint) {
            assertMethodIsStatic(method);
        }
        final var parameterCount           = method.getParameterCount();
        var       parameterUnderInspection = 0;

        if (!isEnvironmentAttribute) {
            assertFirstParameterIsVal(method);
            parameterUnderInspection++;
        }

        var requiresVariables = false;
        if (parameterUnderInspection < parameterCount && parameterTypeIsVariableMap(method, parameterUnderInspection)) {
            requiresVariables = true;
            parameterUnderInspection++;
        }

        if (parameterUnderInspection < parameterCount && parameterTypeIsArrayOfVal(method, parameterUnderInspection)) {
            if (parameterUnderInspection + 1 == parameterCount) {
                return new AttributeSpecification(fullyQualifiedAttributeName, isEnvironmentAttribute,
                        AttributeSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, requiresVariables, List.of(),
                        List.of());
            } else {
                throw new AttributeBrokerException(String.format(VARARGS_MISMATCH_AT_METHOD_S_ERROR, method.getName()));
            }
        }

        var numberOfInnerAttributeParameters = 0;
        for (; parameterUnderInspection < parameterCount; parameterUnderInspection++) {
            if (parameterTypeIsVal(method, parameterUnderInspection)) {
                numberOfInnerAttributeParameters++;
            } else {
                throw new AttributeBrokerException(
                        String.format(NON_VAL_PARAMETER_AT_METHOD_S_ERROR, method.getName()));
            }
        }
        return new AttributeSpecification(fullyQualifiedAttributeName, isEnvironmentAttribute,
                numberOfInnerAttributeParameters, requiresVariables, List.of(), List.of());
    }

    private static String fullyQualifiedAttributeName(Class<?> pipClass, Method method,
            String explicitPolicyInformationPointName, String explicitAttributeName) {
        final var sb = new StringBuilder();
        if (explicitPolicyInformationPointName.isBlank()) {
            sb.append(pipClass.getSimpleName());
        } else {
            sb.append(explicitPolicyInformationPointName);
        }
        sb.append('.');
        if (explicitAttributeName.isBlank()) {
            sb.append(method.getName());
        } else {
            sb.append(explicitAttributeName);
        }
        return sb.toString();
    }

    private static void assertMethodIsStatic(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new AttributeBrokerException(
                    String.format(CLASS_BASED_PIP_NEEDS_STATIC_METHOD_S_ERROR, method.getName()));
        }
    }

    private static void assertValidReturnType(Method method) {
        final var returnType        = method.getReturnType();
        final var genericReturnType = method.getGenericReturnType();
        if (!(genericReturnType instanceof ParameterizedType)) {
            throw new AttributeBrokerException(
                    String.format(RETURN_TYPE_MUST_BE_FLUX_OR_MONO_OF_VALUES_ERROR, returnType.getName()));
        }
        final var returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
        if (!CorePublisher.class.isAssignableFrom(returnType) || !Val.class.isAssignableFrom(returnTypeArgument)) {
            throw new AttributeBrokerException(String.format(RETURN_TYPE_MUST_BE_FLUX_OR_MONO_OF_VALUES_ERROR,
                    returnType.getName(), returnTypeArgument.getName()));
        }
    }

    private static boolean methodReturnsMono(Method method) {
        final var returnType = method.getReturnType();
        return Mono.class.isAssignableFrom(returnType);
    }

    private static boolean parameterTypeIsVal(Method method, int indexOfParameter) {
        return isVal(method.getParameterTypes()[indexOfParameter]);
    }

    private static boolean parameterTypeIsArrayOfVal(Method method, int indexOfParameter) {
        final var parameterType = method.getParameterTypes()[indexOfParameter];
        if (!parameterType.isArray()) {
            return false;
        }
        return isVal(parameterType.getComponentType());
    }

    private static boolean isVal(Class<?> clazz) {
        return Val.class.isAssignableFrom(clazz);
    }

    private static boolean parameterTypeIsVariableMap(Method method, int indexOfParameter) {
        final var parameterTypes = method.getParameterTypes();
        final var genericTypes   = method.getGenericParameterTypes();
        if (!Map.class.isAssignableFrom(parameterTypes[indexOfParameter])) {
            return false;
        }
        final var firstTypeArgument  = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
                .getActualTypeArguments()[0];
        final var secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
                .getActualTypeArguments()[1];
        return String.class.isAssignableFrom(firstTypeArgument) && Val.class.isAssignableFrom(secondTypeArgument);
    }

    private static void assertFirstParameterIsVal(Method method) {
        if (method.getParameterCount() == 0) {
            throw new AttributeBrokerException(String.format(FIRST_PARAMETER_NOT_PRESENT_S_ERROR, method.getName()));
        }
        if (!parameterTypeIsVal(method, 0)) {
            throw new AttributeBrokerException(String.format(FIRST_PARAMETER_S_UNEXPECTED_TYPE_S_ERROR,
                    method.getName(), method.getParameters()[0].getType().getSimpleName()));
        }
    }

}
