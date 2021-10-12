package io.sapl.spring.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ReactiveConstraintEnforcementService {

	private final List<AbstractConstraintHandler> handlerServices;

	public ReactiveConstraintEnforcementService(List<AbstractConstraintHandler> handlerServices) {
		this.handlerServices = new ArrayList<AbstractConstraintHandler>(handlerServices);
		Collections.sort(this.handlerServices);
	}

	public Flux<Object> enforceConstraintsOnResourceAccessPoint(AuthorizationDecision decision,
			Flux<Object> resourceAccessPoint) {
		var wrapped = resourceAccessPoint;
		if (decision.getObligations().isPresent()) {
			for (var constraint : decision.getObligations().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);
				if (handlers.isEmpty())
					return Flux.error(new AccessDeniedException("No handler for obligation: " + constraint.asText()));
				for (var handler : handlers) {
					wrapped = handler.applyObligation(wrapped, constraint);
				}
			}
		}
		if (decision.getAdvice().isPresent()) {
			for (var constraint : decision.getAdvice().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);
				if (handlers.isEmpty())
					log.info("No handler for advice: {}", constraint);
				for (var handler : handlers) {
					wrapped = handler.applyAdvice(wrapped, constraint);
				}
			}
		}
		return wrapped;
	}

	private List<AbstractConstraintHandler> getHandlersResponsibleForConstraint(JsonNode constraint) {
		return handlerServices.stream().filter(handlerService -> handlerService.isResponsible(constraint))
				.collect(Collectors.toList());
	}

	public boolean handleForBlockingMethodInvocationOrAccessDenied(AuthorizationDecision decision) {
		if (decision.getObligations().isPresent()) {
			for (var constraint : decision.getObligations().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);
				if (handlers.isEmpty()) {
					log.warn("No handler for obligation: {}", constraint.asText());
					return false;
				}
				for (var handler : handlers) {
					if (!handler.preBlockingMethodInvocationOrOnAccessDenied(constraint))
						return false;
				}
			}
		}
		if (decision.getAdvice().isPresent()) {
			for (var constraint : decision.getAdvice().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);
				if (handlers.isEmpty())
					log.warn("No handler for advice: {}", constraint.asText());
				for (var handler : handlers)
					handler.preBlockingMethodInvocationOrOnAccessDenied(constraint);
			}
		}
		return true;
	}

	public Object handleAfterBlockingMethodInvocation(AuthorizationDecision decision, Object returnedObject,
			Class<?> returnType) {
		var mappedReturnObject = returnedObject;

		if (decision.getObligations().isPresent()) {
			for (var constraint : decision.getObligations().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);

				if (handlers.isEmpty())
					throw new AccessDeniedException("No handler for obligation: " + constraint.asText());

				for (var handler : handlers) {
					if (returnType == null)
						throw new AccessDeniedException("Generics type not specified in annotation.");
					var handlerFunction = handler.postBlockingMethodInvocation(constraint);
					if (handlerFunction == null)
						throw new AccessDeniedException(
								"Obligation handler indicated that it can handle the obligation, but does not implement postBlockingMethodInvocation.");
					mappedReturnObject = handlerFunction.apply(returnType.cast(returnedObject));
				}
			}
		}

		if (decision.getAdvice().isPresent()) {
			for (var constraint : decision.getAdvice().get()) {
				var handlers = getHandlersResponsibleForConstraint(constraint);
				if (handlers.isEmpty())
					log.warn("No handler for advice: {}", constraint.asText());
				for (var handler : handlers) {
					if (returnType == null)
						throw new AccessDeniedException("Generics type not specified in annotation.");
					var handlerFunction = handler.postBlockingMethodInvocation(constraint);
					if (handlerFunction == null)
						log.warn("No handler implemented for advice when responsibilioty was indicated: {}",
								constraint.asText());
					else
						mappedReturnObject = handlerFunction.apply(returnType.cast(mappedReturnObject));
				}
			}
		}

		return mappedReturnObject;
	}

	public boolean handlePreSubscriptionConstraints(AuthorizationDecision authorizationDecision) {
		System.out.println("apply on preSubscription: " + authorizationDecision);
		return true;
	}

	public Object handleOnNextConstraints(AuthorizationDecision authorizationDecision, Object value) {
		Object transformedValue = handleOnNextObligations(authorizationDecision, value);
		return handleOnNextAdvice(authorizationDecision, transformedValue);
	}

	private Object handleOnNextAdvice(AuthorizationDecision authorizationDecision, Object value) {
		if (authorizationDecision.getAdvice().isEmpty())
			return value;

		var returnValue = value;
		var advice = authorizationDecision.getAdvice().get();
		for (var individualAdvice : advice) {
			for (var handler : handlerServices) {
				if (handler.isResponsible(individualAdvice)) {
					try {
						var handlerFunction = handler.onNext(individualAdvice);
						if (handlerFunction != null)
							handlerFunction.accept(value);
					} catch (Throwable t) {
						Exceptions.throwIfFatal(t);
						log.warn("Failed to execute onNext constraint handler ({}). value={} advice={} error={}",
								handler.getClass().getSimpleName(), value, individualAdvice, t.getMessage());
					}
					try {
						var handlerFunction = handler.onNextMap(individualAdvice);
						if (handlerFunction != null)
							returnValue = handlerFunction.apply(returnValue);
					} catch (Throwable t) {
						Exceptions.throwIfFatal(t);
						log.warn(
								"Failed to execute onNextMap constraint handler ({}). value={} returnValue={} advice={} error={}",
								handler.getClass().getSimpleName(), value, returnValue, individualAdvice,
								t.getMessage());
					}
				}
			}
		}
		return returnValue;
	}

	private Object handleOnNextObligations(AuthorizationDecision authorizationDecision, Object value) {
		if (authorizationDecision.getObligations().isEmpty())
			return value;

		var returnValue = value;
		var obligations = authorizationDecision.getObligations().get();
		for (var obligation : obligations) {
			for (var handler : handlerServices) {
				if (handler.isResponsible(obligation)) {
					try {
						var handlerFunction = handler.onNext(obligation);
						if (handlerFunction != null)
							handlerFunction.accept(value);
					} catch (Throwable t) {
						throw new AccessDeniedException(String.format(
								"Failed to execute onNext constraint handler (%s). value=%s obligation=%s error=%s",
								handler.getClass().getSimpleName(), value, obligation, t.getMessage()));
					}
					try {
						var handlerFunction = handler.onNextMap(obligation);
						if (handlerFunction != null)
							returnValue = handlerFunction.apply(returnValue);
					} catch (Throwable t) {
						throw new AccessDeniedException(String.format(
								"Failed to execute onNextMap constraint handler (%s). value=%s returnValue=%s  obligation=%s error=%s",
								handler.getClass().getSimpleName(), value, returnValue, obligation, t.getMessage()));
					}
				}
			}
		}
		return returnValue;
	}

	public void handleOnCompleteConstraints(AuthorizationDecision authorizationDecision) {
		System.out.println("apply on complete: " + authorizationDecision);
		// TODO Auto-generated method stub
	}

	public void handleOnCancelConstraints(AuthorizationDecision authorizationDecision) {
		System.out.println("apply on cancel: " + authorizationDecision);
		// TODO Auto-generated method stub
	}

	public Throwable handleOnErrorConstraints(AuthorizationDecision authorizationDecision, Throwable error) {
		System.out.println("apply on error: " + authorizationDecision);
		return error;
	}

}
