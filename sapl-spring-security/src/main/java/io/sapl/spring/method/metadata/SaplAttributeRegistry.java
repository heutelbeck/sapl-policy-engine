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
package io.sapl.spring.method.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.util.ClassUtils;

import lombok.NonNull;

public final class SaplAttributeRegistry {
    public static final List<Class<? extends Annotation>> SAPL_ANNOTATIONS = List.of(EnforceRecoverableIfDenied.class,
            EnforceTillDenied.class, EnforceDropWhileDenied.class, PreEnforce.class, PostEnforce.class);

    private final Map<Class<?>, Map<MethodClassKey, SaplAttribute>> cachedAttributes = new ConcurrentHashMap<>();
    private final MethodSecurityExpressionHandler                   expressionHandler;

    public SaplAttributeRegistry() {
        this.expressionHandler = new DefaultMethodSecurityExpressionHandler();
    }

    public SaplAttributeRegistry(@NonNull MethodSecurityExpressionHandler expressionHandler) {
        this.expressionHandler = expressionHandler;
    }

    /**
     * Returns an {@link Optional} {@link SaplAttribute} for the
     * {@link MethodInvocation}.
     *
     * @param <T>            the annotation type
     *
     * @param mi             the {@link MethodInvocation} to use
     * @param annotationType the annotation type.
     * @return the {@link Optional} {@link SaplAttribute} to use
     */
    public <T extends Annotation> Optional<SaplAttribute> getSaplAttributeForAnnotationType(MethodInvocation mi,
            Class<T> annotationType) {
        var method      = mi.getMethod();
        var target      = mi.getThis();
        var targetClass = (target != null) ? target.getClass() : null;
        return getAttribute(method, targetClass, annotationType);
    }

    public boolean hasSpringAnnotations(MethodInvocation mi) {
        var method      = mi.getMethod();
        var target      = mi.getThis();
        var targetClass = (target != null) ? target.getClass() : null;
        return hasAnnotation(method, targetClass, PreAuthorize.class)
                || hasAnnotation(method, targetClass, PostAuthorize.class)
                || hasAnnotation(method, targetClass, PreFilter.class)
                || hasAnnotation(method, targetClass, PostFilter.class);
    }

    private <T extends Annotation> boolean hasAnnotation(Method method, Class<?> targetClass, Class<T> annotationType) {
        var specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        var annotation     = AuthorizationAnnotationUtils
                .findAuthorizeAnnotationOnMethodOrDeclaringClass(specificMethod, annotationType);
        return annotation != null;
    }

    private <A extends Annotation> A findAnnotation(Method method, Class<?> targetClass, Class<A> annotationClass) {
        // The method may be on an interface, but we need attributes from the target
        // class. If the target class is null, the method will be unchanged.
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
        A      annotation     = AnnotationUtils.findAnnotation(specificMethod, annotationClass);
        if (annotation != null)
            return annotation;

        // Check the class-level (note declaringClass, not targetClass, which may not
        // actually implement the method)
        annotation = AnnotationUtils.findAnnotation(specificMethod.getDeclaringClass(), annotationClass);
        return annotation;
    }

    /**
     * @param mi the {@link MethodInvocation} to use
     * @return a Map of all SaplAttributes by type.
     */
    public Map<Class<? extends Annotation>, SaplAttribute> getAllSaplAttributes(MethodInvocation mi) {
        var attributes = new HashMap<Class<? extends Annotation>, SaplAttribute>();
        for (var annotationType : SAPL_ANNOTATIONS) {
            getSaplAttributeForAnnotationType(mi, annotationType).ifPresent(a -> attributes.put(annotationType, a));
        }
        return attributes;
    }

    /**
     * Returns an {@link Optional} {@link SaplAttribute} for the method and the
     * target class.
     *
     * @param <T>            the annotation type
     *
     * @param method         the method
     * @param targetClass    the target class
     * @param annotationType the annotation type
     * @return the {@link Optional} {@link SaplAttribute} to use
     */
    public <T extends Annotation> Optional<SaplAttribute> getAttribute(Method method, Class<?> targetClass,
            Class<T> annotationType) {
        var attributesOfType = this.cachedAttributes.computeIfAbsent(annotationType, x -> new ConcurrentHashMap<>());
        var cacheKey         = new MethodClassKey(method, targetClass);
        var attribute        = attributesOfType.computeIfAbsent(cacheKey,
                x -> resolveAttribute(method, targetClass, annotationType));
        if (attribute == SaplAttribute.NULL_ATTRIBUTE) {
            return Optional.empty();
        }
        return Optional.of(attribute);
    }

    public <T extends Annotation> SaplAttribute resolveAttribute(Method method, Class<?> targetClass,
            Class<T> annotationType) {
        var annotation = findAnnotation(method, targetClass, annotationType);
        if (annotation == null) {
            return SaplAttribute.NULL_ATTRIBUTE;
        }
        if (annotation instanceof PreEnforce saplAnnotation) {
            return new SaplAttribute(annotationType, parseExpression(saplAnnotation.subject()),
                    parseExpression(saplAnnotation.action()), parseExpression(saplAnnotation.resource()),
                    parseExpression(saplAnnotation.environment()), saplAnnotation.genericsType());
        }
        if (annotation instanceof PostEnforce saplAnnotation) {
            return new SaplAttribute(annotationType, parseExpression(saplAnnotation.subject()),
                    parseExpression(saplAnnotation.action()), parseExpression(saplAnnotation.resource()),
                    parseExpression(saplAnnotation.environment()), saplAnnotation.genericsType());
        }
        if (annotation instanceof EnforceRecoverableIfDenied saplAnnotation) {
            return new SaplAttribute(annotationType, parseExpression(saplAnnotation.subject()),
                    parseExpression(saplAnnotation.action()), parseExpression(saplAnnotation.resource()),
                    parseExpression(saplAnnotation.environment()), saplAnnotation.genericsType());
        }
        if (annotation instanceof EnforceTillDenied saplAnnotation) {
            return new SaplAttribute(annotationType, parseExpression(saplAnnotation.subject()),
                    parseExpression(saplAnnotation.action()), parseExpression(saplAnnotation.resource()),
                    parseExpression(saplAnnotation.environment()), saplAnnotation.genericsType());
        }
        if (annotation instanceof EnforceDropWhileDenied saplAnnotation) {
            return new SaplAttribute(annotationType, parseExpression(saplAnnotation.subject()),
                    parseExpression(saplAnnotation.action()), parseExpression(saplAnnotation.resource()),
                    parseExpression(saplAnnotation.environment()), saplAnnotation.genericsType());
        }
        return SaplAttribute.NULL_ATTRIBUTE;
    }

    private Expression parseExpression(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return this.expressionHandler.getExpressionParser().parseExpression(source);
    }

}
