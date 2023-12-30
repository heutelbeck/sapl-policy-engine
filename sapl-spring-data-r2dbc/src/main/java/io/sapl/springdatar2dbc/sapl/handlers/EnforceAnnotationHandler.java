/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatar2dbc.sapl.handlers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatar2dbc.sapl.Enforce;
import lombok.SneakyThrows;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * This service is responsible for processing the {@link Enforce} annotation.
 */
@Service
public class EnforceAnnotationHandler {

    private final BeanFactory               beanFactory;
    private MethodInvocation                methodInvocation;
    private final SpelExpressionParser      parser  = new SpelExpressionParser();
    private final StandardEvaluationContext context = new StandardEvaluationContext();

    EnforceAnnotationHandler(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * The entry method checks whether an {@link Enforce} annotation exists at all
     * and builds an {@link AuthorizationSubscription} accordingly from the
     * information obtained.
     *
     * @param methodInvocation from the interface
     *                         {@link org.aopalliance.intercept.MethodInterceptor}
     * @return the found AuthorizationSubscription.
     */
    public AuthorizationSubscription enforceAnnotation(MethodInvocation methodInvocation) {
        if (Arrays.stream(methodInvocation.getMethod().getAnnotations())
                .noneMatch(annotation -> annotation.annotationType().equals(Enforce.class))) {
            return null;
        }

        this.methodInvocation = methodInvocation;
        var enforceAnnotationValue = methodInvocation.getMethod().getAnnotation(Enforce.class);

        return enforceAnnotationValueToAuthorizationSubscription(enforceAnnotationValue);
    }

    /**
     * The EvaluationContext must be populated with the parameters of the method in
     * order to be resolved.
     *
     * @param context          is the {@link EvaluationContext}
     * @param methodInvocation from the interface
     *                         {@link org.aopalliance.intercept.MethodInterceptor}
     */
    private void setMethodParameterInEvaluationContext(EvaluationContext context, MethodInvocation methodInvocation) {
        var methodParameters = Arrays.stream(methodInvocation.getMethod().getParameters()).map(Parameter::getName)
                .toList();
        var methodArguments  = methodInvocation.getArguments();

        for (int i = 0; i < methodParameters.size(); i++) {
            context.setVariable(methodParameters.get(i), methodArguments[i]);
        }
    }

    /**
     * Extracts the information from the {@link Enforce} annotation and builds an
     * AuthorizationSubscription.
     *
     * @param enforceAnnotation corresponds to the information from the Enforce
     *                          annotation.
     * @return new {@link AuthorizationSubscription}.
     */
    private AuthorizationSubscription enforceAnnotationValueToAuthorizationSubscription(Enforce enforceAnnotation) {
        var subject     = enforceAnnotationValueResolver(enforceAnnotation.subject(),
                enforceAnnotation.staticClasses());
        var action      = enforceAnnotationValueResolver(enforceAnnotation.action(), enforceAnnotation.staticClasses());
        var resource    = enforceAnnotationValueResolver(enforceAnnotation.resource(),
                enforceAnnotation.staticClasses());
        var environment = enforceAnnotationValueResolver(enforceAnnotation.environment(),
                enforceAnnotation.staticClasses());

        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    /**
     * Checks the individual specified attributes of the {@link Enforce} annotation
     * to see if they use any functionality of the {@link EvaluationContext}.
     * <p>
     * 1. if: A static class with full path specification as string 2. if: a static
     * class with specification of the class type via the additional variable named
     * {@link Enforce#staticClasses()} of the Enforce annotation 3. if: a reference
     * to a parameter of the method 4. if: a bean as reference 5. if: a json string
     *
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @param staticClasses   corresponds to all specified static variables
     *                        {@link Enforce#staticClasses()}.
     * @return the resolved final value of the corresponding attribute of an
     *         {@link AuthorizationSubscription}.
     */
    private Object enforceAnnotationValueResolver(String annotationValue, Class<?>[] staticClasses) {

        if (annotationValue.startsWith("T(")) {
            return getObjectByStaticClassWhenValueStartsWithLetterT(annotationValue);
        }

        if (annotationValue.startsWith("#") && annotationValue.contains("(") && annotationValue.contains(")")
                && staticClasses.length != 0) {
            return getObjectByStaticClassWhenValueStartsWithHash(annotationValue, staticClasses);
        }

        if (annotationValue.startsWith("#")) {
            return parseMethodParameterInEvaluationContext(annotationValue);
        }

        if (annotationValue.startsWith("@")) {
            return getObjectByBeanWhenValueStartsWithAt(annotationValue);
        }

        if (annotationValue.trim().startsWith("{") && annotationValue.trim().endsWith("}")) {
            return buildJsonNodeByString(annotationValue);
        }

        return annotationValue;
    }

    /**
     * Converts json string to {@link JsonNode}.
     *
     * @param jsonString is the json string.
     * @return the converted JsonNode.
     */
    @SneakyThrows
    private JsonNode buildJsonNodeByString(Object jsonString) {
        var objectMapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return objectMapper.readTree(jsonString.toString());
    }

    /**
     * Parameters of the method are extracted from the {@link EvaluationContext}.
     *
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @return the value of the method parameter.
     */
    private String parseMethodParameterInEvaluationContext(String annotationValue) {
        setMethodParameterInEvaluationContext(context, methodInvocation);

        try {
            return parser.parseExpression(annotationValue).getValue(context, String.class);
        } catch (EvaluationException ignore) {
            return null;
        }

    }

    /**
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @param staticClasses   contains all specified static classes.
     * @return the value returned by the static class method or just the method
     *         parameter as value.
     * @see EvaluationContext corresponds to a method of a static class. The second
     *      parameter 'staticClasses' contains the corresponding static class. The
     *      value from the {@link EvaluationContext} is extracted.
     */
    @SneakyThrows
    private Object getObjectByStaticClassWhenValueStartsWithHash(String annotationValue, Class<?>[] staticClasses) {
        var methodName = StringUtils.substringBetween(annotationValue, "#", "(");

        return findMethodAndParseExpression(methodName, staticClasses, annotationValue);
    }

    /**
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @param staticClasses   contains all specified static classes.
     * @return the value returned by the static class method.
     * @see EvaluationContext corresponds to a method of a static class. The second
     *      parameter 'staticClasses' contains the corresponding static class. The
     *      value from the {@link EvaluationContext} is extracted.
     */
    @SneakyThrows
    private Object findMethodAndParseExpression(String methodName, Class<?>[] staticClasses, String annotationValue) {
        for (Class<?> clazz : staticClasses) {
            var methods = clazz.getDeclaredMethods();

            for (Method method : methods) {

                if (method.getName().equals(methodName)) {
                    context.registerFunction(methodName, method);
                    setMethodParameterInEvaluationContext(context, methodInvocation);

                    return parser.parseExpression(annotationValue).getValue(context, Object.class);
                }
            }
        }

        throw new NoSuchMethodException("No matching method with the name '" + methodName
                + "' could be found in classes '" + Arrays.toString(staticClasses)
                + "'. Please make sure that the class is static, the method public and not overloaded or consider using another way of @Enforce-Annotation at all. ");

    }

    /**
     * Executes a method using the {@link EvaluationContext}. The method is
     * expressed by a string. The string consists of the path of a static class
     * followed by a method name. This allows the EvaluationContext to execute the
     * desired method.
     *
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @return the value returned by the static class method.
     */
    private Object getObjectByStaticClassWhenValueStartsWithLetterT(String annotationValue) {
        setMethodParameterInEvaluationContext(context, methodInvocation);

        return parser.parseExpression(annotationValue).getValue(context, Object.class);
    }

    /**
     * Executes a method using the {@link EvaluationContext}. The method is
     * expressed by a string. The string consists the name of a bean. This allows
     * the EvaluationContext to call the desired bean.
     *
     * @param annotationValue corresponds the value of a variable of the Enforce
     *                        annotation.
     * @return the value returned by the bean.
     */
    private Object getObjectByBeanWhenValueStartsWithAt(String annotationValue) {
        setMethodParameterInEvaluationContext(context, methodInvocation);
        context.setBeanResolver(new BeanFactoryResolver(this.beanFactory));
        var expression = parser.parseExpression(annotationValue);

        return expression.getValue(context, Object.class);
    }
}
