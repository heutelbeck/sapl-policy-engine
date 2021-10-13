package io.sapl.spring.method.reactive;

import java.lang.reflect.Method;
import java.util.Collection;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.prepost.PostInvocationAttribute;
import org.springframework.security.access.prepost.PreInvocationAttribute;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.method.metadata.EnforceDropWhileDeniedAttribute;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDeniedAttribute;
import io.sapl.spring.method.metadata.EnforceTillDeniedAttribute;
import io.sapl.spring.method.metadata.PostEnforceAttribute;
import io.sapl.spring.method.metadata.PreEnforceAttribute;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
	private final ReactiveConstraintEnforcementService constraintHandlerService;
	@NonNull
	private final ObjectMapper mapper;
	@NonNull
	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		log.trace("Intercepted: {}.{}", invocation.getClass().getSimpleName(), invocation.getMethod().getName());

		var targetClass = invocation.getThis().getClass();
		var method = invocation.getMethod();
		var attributes = source.getAttributes(method, targetClass);

		log.error("ATTRIBUTES: {}", attributes);

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
			throw new UnsupportedOperationException("EnforceRecoverableIfDeniedAttribute unimplemented");

		var preEnforceAttribute = findAttribute(attributes, PreEnforceAttribute.class);
		var postEnforceAttribute = findAttribute(attributes, PostEnforceAttribute.class);
		return interceptWithPrePostEnforce(invocation, preEnforceAttribute, postEnforceAttribute);
	}

	private Flux<?> interceptWithEnforceTillDeniedPEP(MethodInvocation invocation,
			EnforceTillDeniedAttribute attribute) {
		var decisions = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = ((Flux<?>) proceed(invocation));
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint, constraintHandlerService);
	}

	private Flux<?> interceptWithEnforceDropWhileDeniedPEP(MethodInvocation invocation,
			EnforceDropWhileDeniedAttribute attribute) {
		var decisions = preSubscriptionDecisions(invocation, attribute);
		var resourceAccessPoint = ((Flux<?>) proceed(invocation));
		return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, resourceAccessPoint,
				constraintHandlerService);
	}

	private Publisher<?> interceptWithPrePostEnforce(MethodInvocation invocation,
			PreEnforceAttribute preEnforceAttribute, PostEnforceAttribute postEnforceAttribute) {
		var returnType = invocation.getMethod().getReturnType();

		if (Flux.class.isAssignableFrom(returnType))
			return interceptFluxWithPreEnforce(invocation, preEnforceAttribute);

		return interceptMonoWithPreAndPostEnforce(invocation, preEnforceAttribute, postEnforceAttribute);
	}

	private Flux<AuthorizationDecision> preSubscriptionDecisions(MethodInvocation invocation, SaplAttribute attribute) {
		return subscriptionBuilder.reactiveConstructAuthorizationSubscription(invocation, attribute)
				.flatMapMany(authzSubscription -> pdp.decide(authzSubscription));
	}

	private Flux<?> interceptFluxWithPreEnforce(MethodInvocation invocation, PreEnforceAttribute preEnforceAttribute) {
		log.trace("Method interception detected @PreEnforce annotation on a method returning a Flux.");
		var preEnforceAuthorizationDecisions = preSubscriptionDecisions(invocation, preEnforceAttribute);
		@SuppressWarnings("unchecked") // Type is checked warning is false positive
		var resourceAccessPoint = (Flux<Object>) proceed(invocation);
		return preEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, preEnforceAuthorizationDecisions,
				preEnforceAttribute.getGenericsType());
	}

	private Mono<?> interceptMonoWithPreAndPostEnforce(MethodInvocation invocation,
			PreEnforceAttribute preEnforceAttribute, PostEnforceAttribute postEnforceAttribute) {
		Mono<?> wrappedResourceAccessPoint = (Mono<?>) proceed(invocation);

		if (preEnforceAttribute != null) {
			log.trace("Method interception detected @PreEnforce annotation on a method returning a Mono.");
			var preEnforceAuthorizationDecisions = preSubscriptionDecisions(invocation, preEnforceAttribute);
			wrappedResourceAccessPoint = preEnforceOneDecisionOnResourceAccessPoint(
					Flux.from(wrappedResourceAccessPoint), preEnforceAuthorizationDecisions,
					preEnforceAttribute.getGenericsType()).next();
		}

		if (postEnforceAttribute != null) {
			log.trace("Method interception detected @PostEnforce annotation on a method returning a Mono.");
			wrappedResourceAccessPoint = postEnforceOneDecisionOnResourceAccessPoint(wrappedResourceAccessPoint,
					invocation, postEnforceAttribute);
		}

		return wrappedResourceAccessPoint;
	}

	private Mono<?> postEnforceOneDecisionOnResourceAccessPoint(Mono<?> resourceAccessPoint,
			MethodInvocation invocation, PostEnforceAttribute postEnforceAttribute) {
		return resourceAccessPoint.flatMap(result -> {
			Mono<AuthorizationDecision> dec = postEnforceDecision(invocation, postEnforceAttribute, result);
			return dec.flatMap(decision -> {
				Flux<Object> finalResourceAccessPoint = Flux.just(result);
				if (Decision.PERMIT != decision.getDecision())
					finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));
				else if (decision.getResource().isPresent()) {
					try {
						finalResourceAccessPoint = Flux.just(mapper.treeToValue(decision.getResource().get(),
								postEnforceAttribute.getGenericsType()));
					} catch (JsonProcessingException e) {
						finalResourceAccessPoint = Flux.error(new AccessDeniedException(String.format(
								"Access Denied. Error replacing flux contents by resource from PDPs decision: %s",
								e.getMessage())));
					}
				}
				return constraintHandlerService
						.enforceConstraintsOnResourceAccessPoint(decision, finalResourceAccessPoint).next();
			});
		});
	}

	private Mono<AuthorizationDecision> postEnforceDecision(MethodInvocation invocation,
			PostEnforceAttribute postEnforceAttribute, Object returnedObject) {
		return subscriptionBuilder
				.reactiveConstructAuthorizationSubscription(invocation, postEnforceAttribute, returnedObject)
				.flatMapMany(authzSubscription -> pdp.decide(authzSubscription)).next();
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

	private Flux<Object> preEnforceOneDecisionOnResourceAccessPoint(Flux<Object> resourceAccessPoint,
			Flux<AuthorizationDecision> authorizationDecisions, Class<?> clazz) {
		return authorizationDecisions.take(1, true).switchMap(decision -> {
			Flux<Object> finalResourceAccessPoint = resourceAccessPoint;

			if (Decision.PERMIT != decision.getDecision())
				finalResourceAccessPoint = Flux.error(new AccessDeniedException("Access Denied by PDP"));
			else if (decision.getResource().isPresent()) {
				try {
					finalResourceAccessPoint = Flux.just(mapper.treeToValue(decision.getResource().get(), clazz));
				} catch (JsonProcessingException e) {
					finalResourceAccessPoint = Flux.error(new AccessDeniedException(String.format(
							"Access Denied. Error replacing flux contents by resource from PDPs decision: %s",
							e.getMessage())));
				}
			}
			return constraintHandlerService.enforceConstraintsOnResourceAccessPoint(decision, finalResourceAccessPoint)
					// onErrorStop is required to avert an onErrorContinue attack on the RAP. If
					// this is omitted and a downstream consumer does onErrorContinue, the RAP may
					// be accessed by the client.
					.onErrorStop();
		});
	}

	private boolean hasAnyAttributeOfType(Collection<ConfigAttribute> config, Class<?>... attributes) {
		for (var attribute : config)
			for (var clazz : attributes)
				if (clazz.isAssignableFrom(attribute.getClass()))
					return true;
		return false;
	}

	@SneakyThrows
	@SuppressWarnings("unchecked")
	private static <T extends Publisher<?>> T proceed(final MethodInvocation invocation) {
		return (T) invocation.proceed();
	}

	private static <T> T findAttribute(Collection<ConfigAttribute> config, Class<T> clazz) {
		for (var attribute : config)
			if (clazz.isAssignableFrom(attribute.getClass()))
				return clazz.cast(attribute);
		return null;
	}

}
