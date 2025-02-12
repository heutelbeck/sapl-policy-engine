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
package io.sapl.springdatacommon.services;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

/**
 * This service is responsible for processing the {@link QueryEnforce}
 * annotation.
 */
@AllArgsConstructor
public class QueryEnforceAuthorizationSubscriptionService {

    private final BeanFactory               beanFactory;
    private final SecurityExpressionService securityExpressionService;

    private final SpelExpressionParser      parser  = new SpelExpressionParser();
    private final StandardEvaluationContext context = new StandardEvaluationContext();

    private final Pattern jsonStringPattern      = Pattern.compile("\\{.*\\}", Pattern.CASE_INSENSITIVE);
    private final Pattern referenceMethodPattern = Pattern.compile("^#.{1,50}\\(.{0,50}\\)$", Pattern.CASE_INSENSITIVE); // e.g.
                                                                                                                         // #testMethod(123,
                                                                                                                         // 'argument')

    /**
     * The entry method checks whether an {@link QueryEnforce} annotation exists at
     * all and builds an {@link AuthorizationSubscription} accordingly from the
     * information obtained.
     *
     * @param methodInvocation from the interface
     * {@link org.aopalliance.intercept.MethodInterceptor}
     * @return the found AuthorizationSubscription.
     */
    public AuthorizationSubscription getAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce enforceAnnotation) {
        if (enforceAnnotation == null) {
            return null;
        }

        return enforceAnnotationValueToAuthorizationSubscription(enforceAnnotation, methodInvocation);
    }

    /**
     * The EvaluationContext must be populated with the parameters of the method in
     * order to be resolved.
     *
     * @param context is the {@link EvaluationContext}
     * @param methodInvocation from the interface
     * {@link org.aopalliance.intercept.MethodInterceptor}
     */
    private void setMethodParameterInEvaluationContext(EvaluationContext context, MethodInvocation methodInvocation) {
        final var methodParameters = Arrays.stream(methodInvocation.getMethod().getParameters()).map(Parameter::getName)
                .toList();
        final var methodArguments  = methodInvocation.getArguments();

        for (int i = 0; i < methodParameters.size(); i++) {
            context.setVariable(methodParameters.get(i), methodArguments[i]);
        }
    }

    /**
     * Extracts the information from the {@link QueryEnforce} annotation and builds
     * an AuthorizationSubscription.
     *
     * @param enforceAnnotation corresponds to the information from the QueryEnforce
     * annotation.
     * @return new {@link AuthorizationSubscription}.
     */
    private AuthorizationSubscription enforceAnnotationValueToAuthorizationSubscription(QueryEnforce enforceAnnotation,
            MethodInvocation methodInvocation) {
        final var subject     = enforceAnnotationValueResolver(enforceAnnotation.subject(),
                enforceAnnotation.staticClasses(), methodInvocation);
        final var action      = enforceAnnotationValueResolver(enforceAnnotation.action(),
                enforceAnnotation.staticClasses(), methodInvocation);
        final var resource    = enforceAnnotationValueResolver(enforceAnnotation.resource(),
                enforceAnnotation.staticClasses(), methodInvocation);
        final var environment = enforceAnnotationValueResolver(enforceAnnotation.environment(),
                enforceAnnotation.staticClasses(), methodInvocation);

        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    /**
     * Checks the individual specified attributes of the {@link QueryEnforce}
     * annotation to see if they use any functionality of the
     * {@link EvaluationContext}.
     * <p>
     * 1. if: A static class with full path specification as string 2. if: a static
     * class with specification of the class type via the additional variable named
     * {@link QueryEnforce#staticClasses()} of the QueryEnforce annotation 3. if: a
     * reference to a parameter of the method 4. if: a bean as reference 5. if: a
     * json string
     *
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @param staticClasses corresponds to all specified static variables
     * {@link QueryEnforce#staticClasses()}.
     * @return the resolved final value of the corresponding attribute of an
     * {@link AuthorizationSubscription}.
     * @throws ClassNotFoundException
     */
    @SneakyThrows // ClassNotFoundException
    private Object enforceAnnotationValueResolver(String annotationValue, Class<?>[] staticClasses,
            MethodInvocation methodInvocation) {

        if (annotationValue.startsWith("T(")) {
            return getObjectByStaticClassWhenValueStartsWithLetterT(annotationValue, methodInvocation);
        }

        if (referenceMethod(annotationValue)) {
            if (staticClasses.length == 0) {
                final var methodName = StringUtils.substringBetween(annotationValue, "#", "(");

                throw new ClassNotFoundException("No matching method with the name '" + methodName + "' found.");
            }
            return getObjectByStaticClassWhenValueStartsWithHash(annotationValue, staticClasses, methodInvocation);
        }

        if (annotationValue.contains("@")) {
            while (annotationValue.contains("@")) {
                annotationValue = checkAndReplaceBean(annotationValue, methodInvocation);
            }
        }

        if (annotationValue.contains("#")) {
            while (annotationValue.contains("#")) {
                annotationValue = checkAndReplaceVariableReference(annotationValue, methodInvocation);
            }
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            annotationValue = securityExpressionService.evaluateSpelVariables(annotationValue);
            annotationValue = securityExpressionService.evaluateSpelMethods(annotationValue, methodInvocation);
        }

        if (referenceJsonString(annotationValue)) {
            return buildJsonNodeByString(annotationValue);
        }

        return annotationValue;
    }

    private boolean referenceJsonString(String jsonString) {
        if (jsonString == null) {
            return false;
        }
        final var matcher = jsonStringPattern.matcher(jsonString);
        return matcher.matches();
    }

    private boolean referenceMethod(String methodAsString) {
        final var matcher = referenceMethodPattern.matcher(methodAsString);
        return matcher.matches();
    }

    /**
     * Converts json string to {@link JsonNode}.
     *
     * @param jsonString is the json string.
     * @return the converted JsonNode.
     */
    @SneakyThrows // throws JsonMappingException, JsonProcessingException
    private JsonNode buildJsonNodeByString(Object jsonString) {
        final var objectMapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return objectMapper.readTree(jsonString.toString());
    }

    /**
     * Parameters of the method are extracted from the {@link EvaluationContext}.
     *
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @return the value of the method parameter.
     */
    private String parseMethodParameterInEvaluationContext(String annotationValue, MethodInvocation methodInvocation) {
        setMethodParameterInEvaluationContext(context, methodInvocation);

        return parser.parseExpression(annotationValue).getValue(context, String.class);
    }

    /**
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @param staticClasses contains all specified static classes.
     * @return the value returned by the static class method or just the method
     * parameter as value.
     * @see EvaluationContext corresponds to a method of a static class. The second
     * parameter 'staticClasses' contains the corresponding static class. The value
     * from the {@link EvaluationContext} is extracted.
     */
    private Object getObjectByStaticClassWhenValueStartsWithHash(String annotationValue, Class<?>[] staticClasses,
            MethodInvocation methodInvocation) {
        final var methodName = StringUtils.substringBetween(annotationValue, "#", "(");

        return findMethodAndParseExpression(methodName, staticClasses, annotationValue, methodInvocation);
    }

    /**
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @param staticClasses contains all specified static classes.
     * @return the value returned by the static class method.
     * @see EvaluationContext corresponds to a method of a static class. The second
     * parameter 'staticClasses' contains the corresponding static class. The value
     * from the {@link EvaluationContext} is extracted.
     */
    @SneakyThrows // NoSuchMethodException
    private Object findMethodAndParseExpression(String methodName, Class<?>[] staticClasses, String annotationValue,
            MethodInvocation methodInvocation) {
        for (Class<?> clazz : staticClasses) {
            final var methods = clazz.getDeclaredMethods();

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
                + "'. Please make sure that the class is static, the method public and not overloaded or consider using another way of @QueryEnforce-Annotation at all. ");

    }

    /**
     * Executes a method using the {@link EvaluationContext}. The method is
     * expressed by a string. The string consists of the path of a static class
     * followed by a method name. This allows the EvaluationContext to execute the
     * desired method.
     *
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @return the value returned by the static class method.
     */
    private Object getObjectByStaticClassWhenValueStartsWithLetterT(String annotationValue,
            MethodInvocation methodInvocation) {
        setMethodParameterInEvaluationContext(context, methodInvocation);

        return parser.parseExpression(annotationValue).getValue(context, Object.class);
    }

    /**
     * Executes a method using the {@link EvaluationContext}. The method is
     * expressed by a string. The string consists the name of a bean. This allows
     * the EvaluationContext to call the desired bean.
     *
     * @param annotationValue corresponds the value of a variable of the
     * QueryEnforce annotation.
     * @return the value returned by the bean.
     */
    private Object getObjectByBeanWhenValueStartsWithAt(String annotationValue, MethodInvocation methodInvocation) {
        setMethodParameterInEvaluationContext(context, methodInvocation);
        context.setBeanResolver(new BeanFactoryResolver(this.beanFactory));
        final var expression = parser.parseExpression(annotationValue);

        return expression.getValue(context, Object.class);
    }

    private String checkAndReplaceVariableReference(String input, MethodInvocation methodInvocation) {
        final var startIndex     = input.indexOf('#');
        final var partToProcess  = input.substring(startIndex + 1);
        var       reachedEndSign = false;
        var       endIndex       = startIndex;

        for (char c : partToProcess.toCharArray()) {
            if (!reachedEndSign && (Character.isLetterOrDigit(c) || '.' == c)) {
                endIndex++;
            } else {
                reachedEndSign = true;
            }
        }

        final var variable      = input.substring(startIndex, endIndex + 1);
        final var valueResolved = parseMethodParameterInEvaluationContext(variable, methodInvocation);

        return replaceSubstring(input, startIndex, endIndex + 1, valueResolved);
    }

    private String checkAndReplaceBean(String input, MethodInvocation methodInvocation) {
        final var startIndex     = input.indexOf('@');
        final var partToProcess  = input.substring(startIndex + 1);
        var       reachedEndSign = false;
        var       endIndex       = startIndex;

        for (char c : partToProcess.toCharArray()) {
            if (!reachedEndSign && ')' != c) {
                endIndex++;
            } else {
                reachedEndSign = true;
            }
        }

        final var beanName     = input.substring(startIndex, endIndex + 2);
        final var beanResolved = getObjectByBeanWhenValueStartsWithAt(beanName, methodInvocation);

        if (beanResolved != null) {
            return replaceSubstring(input, startIndex, endIndex + 2, beanResolved.toString());
        } else {
            throw new NoSuchBeanDefinitionException("Bean with name '" + beanName + "' could not be found.");
        }

    }

    private String replaceSubstring(String input, int startIndex, int endIndex, String replacement) {
        final var before = input.substring(0, startIndex);
        final var after  = input.substring(endIndex);

        return before + replacement + after;
    }

}
