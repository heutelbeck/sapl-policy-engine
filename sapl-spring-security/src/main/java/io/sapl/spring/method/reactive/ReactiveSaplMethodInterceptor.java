/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.reactive;

import static io.sapl.spring.method.reactive.InvocationUtil.proceed;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import io.sapl.spring.subscriptions.WebfluxAuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReactiveSaplMethodInterceptor implements MethodInterceptor {

	@NonNull
	private final SaplAttributeRegistry source;

	@NonNull
	private final MethodSecurityExpressionHandler handler;

	@NonNull
	private final PolicyDecisionPoint pdp;

	@NonNull
	private final ConstraintEnforcementService constraintHandlerService;

	@NonNull
	private final ObjectMapper mapper;

	@NonNull
	private final WebfluxAuthorizationSubscriptionBuilderService subscriptionBuilder;

	@NonNull
	private final PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint;

	@NonNull
	private final PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint;

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		var method         = invocation.getMethod();
		var saplAttributes = source.getAllSaplAttributes(invocation);

		if (noSaplAnnotationsPresent(saplAttributes)) {
			return null;
		}

		failIfTheAnnotatedMethodIsNotOfReactiveType(method);
		failIfBothSaplAndSpringAnnotationsArePresent(invocation);
		failIfEnforceIsCombinedWithPreEnforceOrPostEnforce(saplAttributes, method);
		failIfPostEnforceIsOnAMethodNotReturningAMono(saplAttributes, method);
		failIfMoreThanOneContinuousEnforceAttributePresent(saplAttributes, method);

		var enforceTillDeniedAttribute = findAttributeForAnnotationType(saplAttributes, EnforceTillDenied.class);
		if (enforceTillDeniedAttribute != null)
			return interceptWithEnforceTillDeniedPEP(invocation, enforceTillDeniedAttribute);

		var enforceDropWhileDeniedAttribute = findAttributeForAnnotationType(saplAttributes,
				EnforceDropWhileDenied.class);
		if (enforceDropWhileDeniedAttribute != null)
			return interceptWithEnforceDropWhileDeniedPEP(invocation, enforceDropWhileDeniedAttribute);

		var enforceRecoverableIfDeniedAttribute = findAttributeForAnnotationType(saplAttributes,
				EnforceRecoverableIfDenied.class);
		if (enforceRecoverableIfDeniedAttribute != null)
			return interceptWithEnforceRecoverableIfDeniedPEP(invocation, enforceRecoverableIfDeniedAttribute);

		var preEnforceAttribute  = findAttributeForAnnotationType(saplAttributes, PreEnforce.class);
		var postEnforceAttribute = findAttributeForAnnotationType(saplAttributes, PostEnforce.class);
		return interceptWithPrePostEnforce(invocation, preEnforceAttribute, postEnforceAttribute);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceRecoverableIfDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions           = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint,
				constraintHandlerService, attribute.genericsType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceTillDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions           = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint, constraintHandlerService,
				attribute.genericsType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceDropWhileDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions           = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint, constraintHandlerService,
				attribute.genericsType());
	}

	private Publisher<?> interceptWithPrePostEnforce(
			MethodInvocation invocation,
			SaplAttribute preEnforceAttribute,
			SaplAttribute postEnforceAttribute) {

		Flux<?> wrappedResourceAccessPoint;
		if (preEnforceAttribute == null) {
			wrappedResourceAccessPoint = Flux.from(proceed(invocation));
		} else {
			var decisions = preSubscriptionDecisions(invocation, preEnforceAttribute);
			wrappedResourceAccessPoint = preEnforcePolicyEnforcementPoint.enforce(decisions,
					invocation, preEnforceAttribute.genericsType());
		}
		if (postEnforceAttribute != null)
			return postEnforcePolicyEnforcementPoint.postEnforceOneDecisionOnResourceAccessPoint(
					wrappedResourceAccessPoint.next(), invocation, postEnforceAttribute);

		var isMonoReturnType = invocation.getMethod().getReturnType().isAssignableFrom(Mono.class);
		if (isMonoReturnType)
			return wrappedResourceAccessPoint.next();

		return wrappedResourceAccessPoint;
	}

	private Flux<AuthorizationDecision> preSubscriptionDecisions(MethodInvocation invocation, SaplAttribute attribute) {
		return subscriptionBuilder.reactiveConstructAuthorizationSubscription(invocation, attribute)
				.flatMapMany(pdp::decide);
	}

	private boolean noSaplAnnotationsPresent(Map<Class<? extends Annotation>, SaplAttribute> attributes) {
		return attributes.isEmpty();
	}

	private void failIfPostEnforceIsOnAMethodNotReturningAMono(
			Map<Class<? extends Annotation>, SaplAttribute> attributes, Method method) {
		var returnType                 = method.getReturnType();
		var hasPostEnforceAttribute    = hasAnyAnnotationOfType(attributes, PostEnforce.class);
		var methodReturnsMono          = Mono.class.isAssignableFrom(returnType);
		var ifPostEnforceThenItIsAMono = !hasPostEnforceAttribute || methodReturnsMono;
		Assert.state(ifPostEnforceThenItIsAMono,
				() -> "The returnType " + returnType + " on " + method + " must be a Mono for @PostEnforce.");
	}

	private void failIfTheAnnotatedMethodIsNotOfReactiveType(Method method) {
		var returnType            = method.getReturnType();
		var hasReactiveReturnType = Publisher.class.isAssignableFrom(returnType);
		Assert.state(hasReactiveReturnType, () -> "The returnType " + returnType + " on " + method
				+ " must be org.reactivestreams.Publisher (i.e. Mono / Flux) in order to support Reactor Context. ");
	}

	private void failIfBothSaplAndSpringAnnotationsArePresent(MethodInvocation mi) {
		var noSpringAttributesPresent = !source.hasSpringAnnotations(mi);
		Assert.state(noSpringAttributesPresent, () -> "Method " + mi.getMethod()
				+ " is annotated by both at least one SAPL annotation (@Enforce..., @PreEnforce, @PostEnforce) and at least one Spring method security annotation (@PreAuthorize, @PostAuthorize, @PostFilter). Please only make use of one type of annotation exclusively.");
	}

	private void failIfEnforceIsCombinedWithPreEnforceOrPostEnforce(
			Map<Class<? extends Annotation>, SaplAttribute> attributes, Method method) {
		var hasEnforceAttribute              = hasAnyAnnotationOfType(attributes, EnforceRecoverableIfDenied.class,
				EnforceTillDenied.class, EnforceDropWhileDenied.class);
		var hasPreOrPostEnforceAttribute     = hasAnyAnnotationOfType(attributes, PreEnforce.class, PostEnforce.class);
		var onlyHasOneTypeOfAnnotationOrNone = !(hasEnforceAttribute && hasPreOrPostEnforceAttribute);
		Assert.state(onlyHasOneTypeOfAnnotationOrNone, () -> "The method " + method
				+ " is annotated by both one of  @EnforceRecoverableIfDenied, @EnforceTillDenied, or @EnforceDropWhileDenied and one of @PreEnforce or @PostEnforce. Please select one mode exclusively.");
	}

	private void failIfMoreThanOneContinuousEnforceAttributePresent(
			Map<Class<? extends Annotation>, SaplAttribute> attributes, Method method) {
		var numberOfContinuousEnforceAttributes = 0;
		if (hasAnyAnnotationOfType(attributes, EnforceRecoverableIfDenied.class))
			numberOfContinuousEnforceAttributes++;
		if (hasAnyAnnotationOfType(attributes, EnforceTillDenied.class))
			numberOfContinuousEnforceAttributes++;
		if (hasAnyAnnotationOfType(attributes, EnforceDropWhileDenied.class))
			numberOfContinuousEnforceAttributes++;

		var onlyHasOneTypeOfContinuousAnnotationOrNone = numberOfContinuousEnforceAttributes == 0
				|| numberOfContinuousEnforceAttributes == 1;
		Assert.state(onlyHasOneTypeOfContinuousAnnotationOrNone, () -> "The method " + method
				+ " must have at most one of @EnforceRecoverableIfDenied, @EnforceTillDenied, or @EnforceDropWhileDenied.");
	}

	@SafeVarargs
	private boolean hasAnyAnnotationOfType(Map<Class<? extends Annotation>, SaplAttribute> config,
			Class<? extends Annotation>... annoatationTypes) {
		for (var annotationType : annoatationTypes)
			if (config.containsKey(annotationType))
				return true;
		return false;
	}

	private static SaplAttribute findAttributeForAnnotationType(Map<Class<? extends Annotation>, SaplAttribute> config,
			Class<?> annotationType) {
		return config.get(annotationType);
	}

}
