/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * This Class holds the different attribute finders and PIPs as a context during
 * evaluation.
 */
@Slf4j
@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

    private static final int REQUIRED_NUMBER_OF_PARAMETERS = 2;
    private static final String NAME_DELIMITER = ".";
    private static final String ATTRIBUTE_NAME_COLLISION_PIP_CONTAINS_MULTIPLE_ATTRIBUTE_METHODS_WITH_NAME = "Attribute name collision. PIP contains multiple attribute methods with name %s";
    private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";
    private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";
    private static final String BAD_NUMBER_OF_PARAMETERS = "Bad number of parameters for attribute finder. Attribute finders are supposed to have at least one Val and one Map<String, JsonNode> as parameters. The method had %d parameters";
    private static final String FIRST_PARAMETER_OF_METHOD_MUST_BE_A_VALUE = "First parameter of method must be a Value. Was: %s";
    private static final String ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES = "Additional parameters of the method must be Flux<Val>. Was: %s.";
    private static final String SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP = "Second parameter of method must be a Map<String, JsonNode>. Was: %s";
    private static final String RETURN_TYPE_MUST_BE_FLUX_OF_VALUES = "The return type of an attribute finder must be Flux<Val>. Was: %s";

    private final Map<String, Collection<String>> attributeNamesByPipName = new HashMap<>();
    private final Map<String, AttributeFinderMetadata> attributeMetadataByAttributeName = new HashMap<>();
    private final Collection<PolicyInformationPointDocumentation> pipDocumentations = new LinkedList<>();

    /**
     * Create the attribute context from a list of PIPs
     *
     * @param policyInformationPoints a list of PIPs
     * @throws InitializationException when loading the PIPs fails
     */
    public AnnotationAttributeContext(Object... policyInformationPoints) throws InitializationException {
        for (Object pip : policyInformationPoints) {
            loadPolicyInformationPoint(pip);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<Val> evaluate(String attribute, Val value, EvaluationContext ctx, Arguments arguments) {
        final AttributeFinderMetadata metadata = attributeMetadataByAttributeName.get(attribute);
        if (metadata == null) {
            return Flux.just(Val.error(UNKNOWN_ATTRIBUTE, attribute));
        }

        final Object pip = metadata.getPolicyInformationPoint();
        final Method method = metadata.getFunction();
        boolean hasLeftHandValue = metadata.leftHandValue;
        boolean hasVariableMap = metadata.variableMap;
        int parameterCount = metadata.parameterCount;

        try {
            if (parameterCount == 0) {
                return (Flux<Val>) method.invoke(pip);
            }

            Object[] argObjects = new Object[arguments.getArgs().size()];
            int i = 0;

            if (hasLeftHandValue) {
                argObjects[i++] = value;
                final Parameter firstParameter = method.getParameters()[0];
                ParameterTypeValidator.validateType(value, firstParameter);
            }

            if (hasVariableMap)
                argObjects[i++] = ctx.getVariableCtx().getVariables();

            for (Expression argument : arguments.getArgs()) {
                argObjects[i++] = argument.evaluate(ctx, Val.UNDEFINED);
            }
            return (Flux<Val>) method.invoke(pip, argObjects);

        } catch (PolicyEvaluationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | IllegalParameterType e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return Flux.just(Val.error(e));
        }
    }

    @Override
    public final void loadPolicyInformationPoint(Object pip) throws InitializationException {
        final Class<?> clazz = pip.getClass();

        final PolicyInformationPoint pipAnnotation = clazz.getAnnotation(PolicyInformationPoint.class);

        if (pipAnnotation == null) {
            throw new InitializationException(CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION);
        }

        String pipName = pipAnnotation.name();
        if (pipName.isEmpty()) {
            pipName = clazz.getSimpleName();
        }
        attributeNamesByPipName.put(pipName, new HashSet<>());
        PolicyInformationPointDocumentation pipDocs = new PolicyInformationPointDocumentation(pipName,
                pipAnnotation.description(), pip);

        pipDocs.setName(pipName);
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Attribute.class)) {
                importAttribute(pip, pipName, pipDocs, method);
            }
        }
        pipDocumentations.add(pipDocs);

    }

    private void importAttribute(Object policyInformationPoint, String pipName,
                                 PolicyInformationPointDocumentation pipDocs, Method method) throws InitializationException {

        final Attribute attAnnotation = method.getAnnotation(Attribute.class);

        String attName = attAnnotation.name();
        if (attName.isEmpty()) {
            attName = method.getName();
        }


        int parameterCount = method.getParameterCount();
        boolean hasLeftHandValue = false;
        boolean hasVariableMap = false;

        if (parameterCount != 0) {
            int customArgumentStartNumber = 0;

            var firstParamIsLeftHandValue = isLeftHandValue(method, 0);
            if (firstParamIsLeftHandValue) {  //environment attribute without left-hand value
                hasLeftHandValue = true;
                var secondParamIsVariableMap = isVariableMap(method, 1);

                if (secondParamIsVariableMap) {
                    validateMapArgumentType(method, 1);
                    hasVariableMap = true;
                }

                customArgumentStartNumber = secondParamIsVariableMap ? 2 : 1;

            } else {
                var firstParamIsVariableMap = isVariableMap(method, 0);
                if (firstParamIsVariableMap) {
                    validateMapArgumentType(method, 0);
                    hasVariableMap = true;
                }


                customArgumentStartNumber = firstParamIsVariableMap ? 1 : 0;
            }

            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Type[] genericTypes = method.getGenericParameterTypes();

            for (int i = customArgumentStartNumber; i < parameterTypes.length; i++) {
                if (!Flux.class.isAssignableFrom(parameterTypes[i])) {
                    throw new InitializationException(ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES,
                            parameterTypes[i]);
                }
                final Type fluxContentType = ((ParameterizedType) genericTypes[i]).getActualTypeArguments()[0];
                if (!Val.class.isAssignableFrom((Class<?>) fluxContentType)) {
                    throw new InitializationException(ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES,
                            genericTypes[i]);
                }
            }
        }

        validateReturnType(method);

        if (pipDocs.documentation.containsKey(attName)) {
            throw new InitializationException(
                    ATTRIBUTE_NAME_COLLISION_PIP_CONTAINS_MULTIPLE_ATTRIBUTE_METHODS_WITH_NAME, attName);
        }

        pipDocs.documentation.put(attName, attAnnotation.docs());

        attributeMetadataByAttributeName.put(fullName(pipName, attName),
                new AttributeFinderMetadata(policyInformationPoint, method, hasLeftHandValue, hasVariableMap, parameterCount));

        attributeNamesByPipName.get(pipName).add(attName);
    }

    private void validateReturnType(Method method) throws InitializationException {
        final Class<?> returnType = method.getReturnType();
        final Type genericReturnType = method.getGenericReturnType();
        if (!(genericReturnType instanceof ParameterizedType)) {
            throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES, returnType.getName());
        }

        final Class<?> returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType)
                .getActualTypeArguments()[0];
        if (!Flux.class.isAssignableFrom(returnType) || !Val.class.isAssignableFrom(returnTypeArgument)) {
            throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES,
                    returnType.getName() + "<" + returnTypeArgument.getName() + ">");
        }
    }


    private void validateMapArgumentType(Method method, int variableMapParameter) throws InitializationException {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final Type[] genericTypes = method.getGenericParameterTypes();

        final Class<?> firstTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[variableMapParameter]).getActualTypeArguments()[0];
        final Class<?> secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[variableMapParameter]).getActualTypeArguments()[1];

        if (!String.class.isAssignableFrom(firstTypeArgument) || !JsonNode.class.isAssignableFrom(secondTypeArgument)) {
            throw new InitializationException(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[variableMapParameter].getName()
                    + "<" + firstTypeArgument.getName() + "," + secondTypeArgument.getName() + ">");
        }
    }

    private boolean isLeftHandValue(Method method, int parameterNumber) {
        final Class<?>[] parameterTypes = method.getParameterTypes();

        return Val.class.isAssignableFrom(parameterTypes[parameterNumber]);
    }

    private boolean isVariableMap(Method method, int parameterNumber) {
        final Class<?>[] parameterTypes = method.getParameterTypes();

        return Map.class.isAssignableFrom(parameterTypes[parameterNumber]);
    }

    private static String fullName(String packageName, String methodName) {
        return packageName + NAME_DELIMITER + methodName;
    }

    @Override
    public Boolean isProvidedFunction(String attribute) {
        return attributeMetadataByAttributeName.containsKey(attribute);
    }

    @Override
    public Collection<PolicyInformationPointDocumentation> getDocumentation() {
        return Collections.unmodifiableCollection(pipDocumentations);
    }

    @Override
    public Collection<String> providedFunctionsOfLibrary(String pipName) {
        Collection<String> pips = attributeNamesByPipName.get(pipName);
        if (pips != null)
            return pips;
        else
            return new HashSet<>();
    }

    /**
     * Metadata for attribute finders.
     */
    @Data
    @AllArgsConstructor
    public static class AttributeFinderMetadata {
        @NonNull
        Object policyInformationPoint;
        @NonNull
        Method function;
        boolean leftHandValue;
        boolean variableMap;
        int parameterCount;

    }

    @Override
    public Collection<String> getAvailableLibraries() {
        return this.attributeNamesByPipName.keySet();
    }

}
