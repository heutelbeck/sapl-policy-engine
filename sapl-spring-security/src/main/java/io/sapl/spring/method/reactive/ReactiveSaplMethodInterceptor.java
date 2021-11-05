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
package io.sapl.spring.method.reactive;

import static io.sapl.spring.method.reactive.InvocationUtil.proceed;

import java.lang.reflect.Method;
import java.util.Collection;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.prepost.PostInvocationAttribute;
import org.springframework.security.access.prepost.PreInvocationAttribute;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.method.metadata.EnforceDropWhileDeniedAttribute;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDeniedAttribute;
import io.sapl.spring.method.metadata.EnforceTillDeniedAttribute;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class ReactiveSaplMethodInterceptor implements MethodInterceptor {

	private static final Class<?>[] SAPL_ATTRIBUTES = { EnforceRecoverableIfDeniedAttribute.class,
			EnforceTillDeniedAttribute.class, EnforceDropWhileDeniedAttribute.class, PreEnforceAttribute.class,
			PostEnforceAttribute.class };

	private static final Class<?>[] SPRING_ATTRIBUTES = { PostInvocationAttribute.class, PreInvocationAttribute.class };

	@NonNull
	private final MethodInterceptor springSecurityMethodInterceptor;

	@NonNull
	private final MethodSecurityMetadataSource source;

	@NonNull
	private final MethodSecurityExpressionHandler handler;

	@NonNull
	private final PolicyDecisionPoint pdp;

	@NonNull
	private final ConstraintEnforcementService constraintHandlerService;

	@NonNull
	private final ObjectMapper mapper;

	@NonNull
	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;

	@NonNull
	private final PreEnforcePolicyEnforcementPoint preEnforcePolicyEnforcementPoint;

	@NonNull
	private final PostEnforcePolicyEnforcementPoint postEnforcePolicyEnforcementPoint;

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		log.trace("Intercepted: {}.{}", invocation.getClass().getSimpleName(), invocation.getMethod().getName());

		var targetClass = invocation.getThis().getClass();
		var method = invocation.getMethod();
		var attributes = source.getAttributes(method, targetClass);

		if (noSaplAnnotationsPresent(attributes))
			return delegateToSpringSecurityInterceptor(invocation);

		failIfTheAnnotatedMethodIsNotOfReactiveType(method);
		failIfBothSaplAndSpringAnnotationsArePresent(attributes, method);
		failIfEnforceIsCombinedWithPreEnforceOrPostEnforce(attributes, method);
		failIfPostEnforceIsOnAMethodNotReturningAMono(attributes, method);
		failIfMoreThanOneContiniousEnforceAttributePresent(attributes, method);

		var enforceTillDeniedAttribute = findAttribute(attributes, EnforceTillDeniedAttribute.class);
		if (enforceTillDeniedAttribute != null)
			return interceptWithEnforceTillDeniedPEP(invocation, enforceTillDeniedAttribute);

		var enforceDropWhileDeniedAttribute = findAttribute(attributes, EnforceDropWhileDeniedAttribute.class);
		if (enforceDropWhileDeniedAttribute != null)
			return interceptWithEnforceDropWhileDeniedPEP(invocation, enforceDropWhileDeniedAttribute);

		var enforceRecoverableIfDeniedAttribute = findAttribute(attributes, EnforceRecoverableIfDeniedAttribute.class);
		if (enforceRecoverableIfDeniedAttribute != null)
			return interceptWithEnforceRecoverableIfDeniedPEP(invocation, enforceRecoverableIfDeniedAttribute);

		var preEnforceAttribute = findAttribute(attributes, PreEnforceAttribute.class);
		var postEnforceAttribute = findAttribute(attributes, PostEnforceAttribute.class);
		return interceptWithPrePostEnforce(invocation, preEnforceAttribute, postEnforceAttribute);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceRecoverableIfDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint,
				constraintHandlerService, attribute.getGenericsType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceTillDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint, constraintHandlerService,
				attribute.getGenericsType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Flux<?> interceptWithEnforceDropWhileDeniedPEP(MethodInvocation invocation, SaplAttribute attribute) {
		var decisions = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = (Flux) proceed(invocation);
		return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint, constraintHandlerService,
				attribute.getGenericsType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Publisher<?> interceptWithPrePostEnforce(MethodInvocation invocation,
			PreEnforceAttribute preEnforceAttribute, PostEnforceAttribute postEnforceAttribute) {
		var resourceAccessPoint = proceed(invocation);
		var wrappedResourceAccessPoint = Flux.from(resourceAccessPoint);
		if (preEnforceAttribute != null) {
			var decisions = preSubscriptionDecisions(invocation, preEnforceAttribute);
			wrappedResourceAccessPoint = preEnforcePolicyEnforcementPoint.enforce(decisions,
					(Flux) wrappedResourceAccessPoint, preEnforceAttribute.getGenericsType());
		}
		if (postEnforceAttribute != null)
			return postEnforcePolicyEnforcementPoint.postEnforceOneDecisionOnResourceAccessPoint(
					wrappedResourceAccessPoint.next(), invocation, postEnforceAttribute);

		if (resourceAccessPoint instanceof Mono)
			return wrappedResourceAccessPoint.next();

		return wrappedResourceAccessPoint;
	}

	private Flux<AuthorizationDecision> preSubscriptionDecisions(MethodInvocation invocation, SaplAttribute attribute) {
		return subscriptionBuilder.reactiveConstructAuthorizationSubscription(invocation, attribute)
				.flatMapMany(pdp::decide);
	}

	private Object delegateToSpringSecurityInterceptor(final MethodInvocation invocation) throws Throwable {
		return springSecurityMethodInterceptor.invoke(invocation);
	}

	private boolean noSaplAnnotationsPresent(Collection<ConfigAttribute> attributes) {
		return !hasAnyAttributeOfType(attributes, SAPL_ATTRIBUTES);
	}

	private void failIfPostEnforceIsOnAMethodNotReturningAMono(Collection<ConfigAttribute> attributes, Method method) {
		var returnType = method.getReturnType();
		var hasPostEnforceAttribute = hasAnyAttributeOfType(attributes, PostEnforceAttribute.class);
		var methodReturnsMono = Mono.class.isAssignableFrom(returnType);
		var ifPostEnforceThenItIsAMono = !hasPostEnforceAttribute || methodReturnsMono;
		Assert.state(ifPostEnforceThenItIsAMono,
				() -> "The returnType " + returnType + " on " + method + " must be a Mono for @PostEnforce.");
	}

	private void failIfTheAnnotatedMethodIsNotOfReactiveType(Method method) {
		var returnType = method.getReturnType();
		var hasReactiveReturnType = Publisher.class.isAssignableFrom(returnType);
		Assert.state(hasReactiveReturnType, () -> "The returnType " + returnType + " on " + method
				+ " must be org.reactivestreams.Publisher (i.e. Mono / Flux) in order to support Reactor Context. ");
	}

	private void failIfBothSaplAndSpringAnnotationsArePresent(Collection<ConfigAttribute> attributes, Method method) {
		var noSpringAttributesPresent = !hasAnyAttributeOfType(attributes, SPRING_ATTRIBUTES);
		Assert.state(noSpringAttributesPresent, () -> "The method " + method
				+ " is annotated by both at least one SAPL annotation (@Enfore, @PreEnforce, @PostEnforce) and at least one Spring method security annotation (@PreAuthorize, @PostAuthorize, @PostFilter). Please only make use of one type of annotation exclusively.");
	}

	private void failIfEnforceIsCombinedWithPreEnforceOrPostEnforce(Collection<ConfigAttribute> attributes,
			Method method) {
		var hasEnforceAttribute = hasAnyAttributeOfType(attributes, EnforceRecoverableIfDeniedAttribute.class,
				EnforceTillDeniedAttribute.class, EnforceDropWhileDeniedAttribute.class);
		var hasPreOrPostEnforceAttribute = hasAnyAttributeOfType(attributes, PreEnforceAttribute.class,
				PostEnforceAttribute.class);
		var onlyHasOneTypeOfAnnotationOrNone = !(hasEnforceAttribute && hasPreOrPostEnforceAttribute);
		Assert.state(onlyHasOneTypeOfAnnotationOrNone, () -> "The method " + method
				+ " is annotated by both one of  @EnforceRecoverableIfDenied, @EnforceTillDenied, or @EnforceDropWhileDenied and one of @PreEnforce or @PostEnforce. Please select one mode exclusively.");
	}

	private void failIfMoreThanOneContiniousEnforceAttributePresent(Collection<ConfigAttribute> attributes,
			Method method) {
		var numberOfContiniousEnforceAttributes = 0;
		if (hasAnyAttributeOfType(attributes, EnforceRecoverableIfDeniedAttribute.class))
			numberOfContiniousEnforceAttributes++;
		if (hasAnyAttributeOfType(attributes, EnforceTillDeniedAttribute.class))
			numberOfContiniousEnforceAttributes++;
		if (hasAnyAttributeOfType(attributes, EnforceDropWhileDeniedAttribute.class))
			numberOfContiniousEnforceAttributes++;

		var onlyHasOneTypeOfContinousAnnotationOrNone = numberOfContiniousEnforceAttributes == 0
				|| numberOfContiniousEnforceAttributes == 1;
		Assert.state(onlyHasOneTypeOfContinousAnnotationOrNone, () -> "The method " + method
				+ " must have at most one of @EnforceRecoverableIfDenied, @EnforceTillDenied, or @EnforceDropWhileDenied.");
	}

	private boolean hasAnyAttributeOfType(Collection<ConfigAttribute> config, Class<?>... attributes) {
		for (var attribute : config)
			for (var clazz : attributes)
				if (clazz.isAssignableFrom(attribute.getClass()))
					return true;
		return false;
	}

	private static <T> T findAttribute(Collection<ConfigAttribute> config, Class<T> clazz) {
		for (var attribute : config)
			if (clazz.isAssignableFrom(attribute.getClass()))
				return clazz.cast(attribute);
		return null;
	}

}
