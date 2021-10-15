package io.sapl.spring.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

	public void handleOnSubscribeConstraints(AuthorizationDecision authorizationDecision, Subscription subscription) {
		handleConsumerConstraints(authorizationDecision, (handler, constraint) -> handler.onSubscribe(constraint),
				"onSubscribe", subscription);
	}

	public void handleOnCompleteConstraints(AuthorizationDecision authorizationDecision) {
		handleRunnableConstraints(authorizationDecision, (handler, constraint) -> handler.onComplete(constraint),
				"onComplete");
	}

	public void handleOnCancelConstraints(AuthorizationDecision authorizationDecision) {
		handleRunnableConstraints(authorizationDecision, (handler, constraint) -> handler.onCancel(constraint),
				"onCancel");
	}

	public void handleOnTerminateConstraints(AuthorizationDecision authorizationDecision) {
		handleRunnableConstraints(authorizationDecision, (handler, constraint) -> handler.onTerminate(constraint),
				"onTerminate");
	}

	public void handleAfterTerminateConstraints(AuthorizationDecision authorizationDecision) {
		handleRunnableConstraints(authorizationDecision, (handler, constraint) -> handler.afterTerminate(constraint),
				"afterTerminate");
	}

	public void handleOnRequestConstraints(AuthorizationDecision authorizationDecision, Long value) {
		handleConsumerConstraints(authorizationDecision, (handler, constraint) -> handler.onRequest(constraint),
				"onRequest", value);
	}

	public Object handleOnNextConstraints(AuthorizationDecision authorizationDecision, Object value) {
		handleConsumerConstraints(authorizationDecision, (handler, constraint) -> handler.onNext(constraint), "onNext",
				value);
		return handleTransformingConstraints(authorizationDecision,
				(handler, constraint) -> handler.onNextMap(constraint), "onNextMap", value);
	}

	public Throwable handleOnErrorConstraints(AuthorizationDecision authorizationDecision, Throwable error) {
		handleConsumerConstraints(authorizationDecision, (handler, constraint) -> handler.onError(constraint),
				"onError", error);
		return handleTransformingConstraints(authorizationDecision,
				(handler, constraint) -> handler.onErrorMap(constraint), "onErrorMap", error);
	}

	private void handleRunnableConstraints(AuthorizationDecision authorizationDecision,
			BiFunction<AbstractConstraintHandler, JsonNode, Runnable> handlerSource, String signalName) {
		handleRunnableConstraints(authorizationDecision.getObligations(), handlerSource, signalName, true);
		handleRunnableConstraints(authorizationDecision.getAdvice(), handlerSource, signalName, false);
	}

	private void handleRunnableConstraints(Optional<ArrayNode> constraints,
			BiFunction<AbstractConstraintHandler, JsonNode, Runnable> handlerSource, String signalName,
			boolean isObligation) {

		if (constraints.isEmpty())
			return;
		var constraintArray = constraints.get();
		for (var constraint : constraintArray) {
			var handlerFound = false;
			for (var handler : handlerServices) {
				if (!handler.isResponsible(constraint))
					continue;
				handlerFound = true;
				try {
					var handlerFunction = handlerSource.apply(handler, constraint);
					if (handlerFunction != null)
						handlerFunction.run();
				} catch (Throwable t) {
					var message = String.format(
							"Failed to execute %s constraint handler (%s). constraint=%s isObligation=%s error=%s",
							signalName, handler.getClass().getName(), constraint, isObligation, t.getMessage());
					logAndThrowIfObligationOrFatal(t, message, isObligation);
				}
			}
			if (!handlerFound)
				logNoHandlerFoundAndThrowIfObligation(constraint, isObligation);
		}
	}

	private <T> void handleConsumerConstraints(AuthorizationDecision authorizationDecision,
			BiFunction<AbstractConstraintHandler, JsonNode, Consumer<T>> handlerSource, String signalName, T value) {
		handleConsumerConstraints(authorizationDecision.getObligations(), handlerSource, signalName, value, true);
		handleConsumerConstraints(authorizationDecision.getAdvice(), handlerSource, signalName, value, false);
	}

	private <T> void handleConsumerConstraints(Optional<ArrayNode> constraints,
			BiFunction<AbstractConstraintHandler, JsonNode, Consumer<T>> handlerSource, String signalName, T value,
			boolean isObligation) {
		if (constraints.isEmpty())
			return;
		var constraintArray = constraints.get();
		for (var constraint : constraintArray) {
			var handlerFound = false;
			for (var handler : handlerServices) {
				if (!handler.isResponsible(constraint))
					continue;
				handlerFound = true;
				try {
					var handlerFunction = handlerSource.apply(handler, constraint);
					if (handlerFunction != null)
						handlerFunction.accept(value);
				} catch (Throwable t) {
					var message = String.format(
							"Failed to execute %s constraint handler (%s). constraint=%s value=%s isObligation=%s error=%s",
							signalName, handler.getClass().getName(), constraint.asText(), value, isObligation,
							t.getMessage());
					logAndThrowIfObligationOrFatal(t, message, isObligation);
				}
			}
			if (!handlerFound)
				logNoHandlerFoundAndThrowIfObligation(constraint, isObligation);
		}
	}

	private <T> T handleTransformingConstraints(AuthorizationDecision authorizationDecision,
			BiFunction<AbstractConstraintHandler, JsonNode, Function<T, T>> handlerSource, String signalName, T value) {
		T transformedValue = handleTransformingConstraints(authorizationDecision.getObligations(), handlerSource,
				signalName, value, true);
		return handleTransformingConstraints(authorizationDecision.getAdvice(), handlerSource, signalName,
				transformedValue, false);
	}

	private <T> T handleTransformingConstraints(Optional<ArrayNode> constraints,
			BiFunction<AbstractConstraintHandler, JsonNode, Function<T, T>> handlerSource, String signalName, T value,
			boolean isObligation) {
		if (constraints.isEmpty())
			return value;
		var returnValue = value;
		var constraintArray = constraints.get();
		for (var constraint : constraintArray) {
			var handlerFound = false;
			for (var handler : handlerServices) {
				if (!handler.isResponsible(constraint))
					continue;
				handlerFound = true;
				try {
					var handlerFunction = handlerSource.apply(handler, constraint);
					if (handlerFunction != null)
						returnValue = handlerFunction.apply(returnValue);

				} catch (Throwable t) {
					var message = String.format(
							"Failed to execute %s constraint handler (%s). constraint=%s value=%s returnValue=%s isObligation=%s error=%s",
							signalName, handler.getClass().getName(), constraint, value, returnValue, isObligation,
							t.getMessage());
					logAndThrowIfObligationOrFatal(t, message, isObligation);
				}
			}
			if (!handlerFound)
				logNoHandlerFoundAndThrowIfObligation(constraint, isObligation);
		}
		return returnValue;
	}

	private void logAndThrowIfObligationOrFatal(Throwable t, String message, boolean isObligation) {
		log.warn(message);
		Exceptions.throwIfFatal(t);
		if (isObligation)
			throw new AccessDeniedException(message, t);
	}

	private void logNoHandlerFoundAndThrowIfObligation(JsonNode constraint, boolean isObligation) {
		var message = String.format("Unable to find handler for constraint %s", constraint.asText());
		log.warn(message);
		if (isObligation)
			throw new AccessDeniedException(message);
	}
}
