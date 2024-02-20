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
package io.sapl.spring.method.blocking;

import java.lang.annotation.Annotation;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.Ordered;

import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.Getter;
import lombok.NonNull;

public class PolicyEnforcementPointAroundMethodInterceptor
        implements Ordered, MethodInterceptor, PointcutAdvisor, AopInfrastructureBean {

    @Getter
    private final Pointcut          pointcut;
    @Getter
    private final int               order;
    private final MethodInterceptor policyEnforcementPoint;

    public static PolicyEnforcementPointAroundMethodInterceptor preEnforce(MethodInterceptor policyEnforcementPoint) {
        return new PolicyEnforcementPointAroundMethodInterceptor(PreEnforce.class,
                SaplAuthorizationInterceptorsOrder.PRE_ENFORCE.getOrder(), policyEnforcementPoint);
    }

    public static PolicyEnforcementPointAroundMethodInterceptor postEnforce(MethodInterceptor policyEnforcementPoint) {
        return new PolicyEnforcementPointAroundMethodInterceptor(PostEnforce.class,
                SaplAuthorizationInterceptorsOrder.POST_ENFORCE.getOrder(), policyEnforcementPoint);
    }

    public static PolicyEnforcementPointAroundMethodInterceptor reactive(MethodInterceptor policyEnforcementPoint) {
        return new PolicyEnforcementPointAroundMethodInterceptor(
                SaplAuthorizationInterceptorsOrder.PRE_ENFORCE.getOrder(), policyEnforcementPoint);
    }

    PolicyEnforcementPointAroundMethodInterceptor(int order, MethodInterceptor policyEnforcementPoint) {
        this.pointcut               = pointcutForAllAnnotations();
        this.order                  = order;
        this.policyEnforcementPoint = policyEnforcementPoint;
    }

    PolicyEnforcementPointAroundMethodInterceptor(Class<? extends Annotation> annotation, int order,
            MethodInterceptor policyEnforcementPoint) {
        this.pointcut               = pointcutForAnnotation(annotation);
        this.order                  = order;
        this.policyEnforcementPoint = policyEnforcementPoint;
    }

    private static Pointcut pointcutForAnnotation(Class<? extends Annotation> annotation) {
        return new ComposablePointcut(classOrMethod(annotation));

    }

    private static Pointcut pointcutForAllAnnotations() {
        var cut = new ComposablePointcut(classOrMethod(PreEnforce.class));
        cut = cut.union(classOrMethod(PostEnforce.class));
        cut = cut.union(classOrMethod(EnforceRecoverableIfDenied.class));
        cut = cut.union(classOrMethod(EnforceDropWhileDenied.class));
        return cut.union(classOrMethod(EnforceTillDenied.class));
    }

    private static Pointcut classOrMethod(Class<? extends Annotation> annotation) {
        return Pointcuts.union(new AnnotationMatchingPointcut(null, annotation, true),
                new AnnotationMatchingPointcut(annotation, true));
    }

    @Override
    public @NonNull Advice getAdvice() {
        return this;
    }

    @Override
    public boolean isPerInstance() {
        return false;
    }

    @Override
    public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
        return policyEnforcementPoint.invoke(invocation);
    }
}
