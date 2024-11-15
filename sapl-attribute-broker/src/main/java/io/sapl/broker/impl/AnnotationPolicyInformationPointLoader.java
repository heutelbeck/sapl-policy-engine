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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

import io.sapl.api.broker.AttributeStreamBroker;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import lombok.RequiredArgsConstructor;
import reactor.core.CorePublisher;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class AnnotationPolicyInformationPointLoader {

    static final String FIRST_PARAMETER_NOT_PRESENT_S_ERROR                     = "Argument missing. First parameter of the method '%s' must be a Val for taking in the left-hand argument, but no argument was present.";
    static final String FIRST_PARAMETER_S_UNEXPECTED_S_ERROR                    = "First parameter of the method %s has an unexpected type. Was expecting a Val but got %s.";
    static final String A_PIP_WITH_THE_NAME_S_HAS_ALREADY_BEEN_REGISTERED_ERROR = "A PIP with the name '%s' has already been registered.";
    static final String NO_POLICY_INFORMATION_POINT_ANNOTATION_ERROR            = "Provided class has no @PolicyInformationPoint annotation.";
    static final String UNKNOWN_ATTRIBUTE_ERROR                                 = "Unknown attribute %s";
    static final String RETURN_TYPE_MUST_BE_FLUX_OR_MONO_OF_VALUES_ERROR        = "The return type of an attribute finder must be Flux<Val> or Mono<Val>. Was: %s";
    static final String MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED                 = "Attribute has both a schema and a schemaPath annotation. Multiple schema annotations are not allowed.";
    static final String INVALID_SCHEMA_DEFINITION                               = "Invalid schema definition for attribute found. This only validated JSON syntax, not compliance with JSONSchema specification";

    private final AttributeStreamBroker broker;

    /**
     * Initialize with context from a supplied PIPs.
     *
     * @param pipSupplier supplies instantiated libraries
     * @param staticPipSupplier supplies libraries contained in utility classes with
     * static methods as functions
     * @throws AttributeBrokerException
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
     * @throws AttributeBrokerException
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
     * @throws AttributeBrokerException
     */
    public void loadPolicyInformationPoints(PolicyInformationPointSupplier pipSupplier) {
        for (final var pip : pipSupplier.get()) {
            loadPolicyInformationPoint(pip);
        }
    }

    public void loadPolicyInformationPoint(Object policyInformationPoint) {
        loadPolicyInformationPoint(policyInformationPoint, policyInformationPoint.getClass());

    }

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
            }
        }

        if (!foundAtLeastOneSuppliedAttributeInPip) {
            throw new AttributeBrokerException("The PIP with the name '" + pipName
                    + "' does not declare any attributes. To declare an attribute, annotate a method with @Attribute or @EnvironmentAttribute.");
        }
    }

    private AttributeFinder createEnvironmentAttributeFinder(Object policyInformationPoint, Method method,
            EnvironmentAttribute annotation, AttributeFinderSpecification specification) {
        return null;
    }

    private AttributeFinder createAttributeFinder(Object policyInformationPoint, Method method, Attribute annotation,
            AttributeFinderSpecification specification) {
        assertValidReturnType(method);
        final var methodReturnsMono = methodReturnsMono(method);
        return null;
    }

    private static AttributeFinderSpecification getSpecificationOfAttribute(Object policyInformationPoint,
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
                return new AttributeFinderSpecification(fullyQualifiedAttributeName, isEnvironmentAttribute,
                        AttributeFinderSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, requiresVariables);
            } else {
                throw new AttributeBrokerException("The method " + method.getName()
                        + " has an array of Val as a parameter, which indicates a variable number of arguments."
                        + " However the array is followed by some other parameters. This is prohibited."
                        + " The array must be the last parameter of the attribute declaration.");
            }
        }

        var numberOfInnerAttributeParameters = 0;
        for (; parameterUnderInspection < parameterCount; parameterUnderInspection++) {
            if (parameterTypeIsVal(method, parameterUnderInspection)) {
                numberOfInnerAttributeParameters++;
            } else {
                throw new AttributeBrokerException(
                        "The method " + method.getName() + " declared a non Val as a parameter");
            }
        }
        return new AttributeFinderSpecification(fullyQualifiedAttributeName, isEnvironmentAttribute,
                numberOfInnerAttributeParameters, requiresVariables);
    }

    private static void assertMethodIsStatic(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new AttributeBrokerException(
                    "Cannot initialize PIPs. If no PIP instance is provided, the method of an attribute finder must be static. "
                            + method.getName()
                            + " is not static. In case your PIP implementation cannot have the method as static because it "
                            + "depends on PIP state or injected dependencies, make sure to register the PIP as an instance "
                            + "instead of a class.");
        }
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
                    returnType.getName() + '<' + returnTypeArgument.getName() + '>'));
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
            throw new AttributeBrokerException(String.format(FIRST_PARAMETER_S_UNEXPECTED_S_ERROR, method.getName(),
                    method.getParameters()[0].getType().getSimpleName()));
        }
    }

}
