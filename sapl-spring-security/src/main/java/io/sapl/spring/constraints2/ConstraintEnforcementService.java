package io.sapl.spring.constraints2;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider.Signal;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ConstraintEnforcementService {

	private final List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;
	private final List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;
	private final List<RequestHandlerProvider> globalRequestHandlerProviders;
	private final List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;
	private final List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;
	private final List<ErrorHandlerProvider> globalErrorHandlerProviders;
	private final ObjectMapper mapper;
	private final SortedSetMultimap<Signal, RunnableConstraintHandlerProvider> globalRunnableIndex;

	public ConstraintEnforcementService(List<RunnableConstraintHandlerProvider> globalRunnableProviders,
			List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
			List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders,
			List<RequestHandlerProvider> globalRequestHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<ErrorHandlerProvider> globalErrorHandlerProviders, ObjectMapper mapper) {
		this.globalConsumerProviders = globalConsumerProviders;
		Collections.sort(this.globalConsumerProviders);
		this.globalSubscriptionHandlerProviders = globalSubscriptionHandlerProviders;
		Collections.sort(this.globalSubscriptionHandlerProviders);
		this.globalRequestHandlerProviders = globalRequestHandlerProviders;
		Collections.sort(this.globalRequestHandlerProviders);
		this.globalMappingHandlerProviders = globalMappingHandlerProviders;
		Collections.sort(this.globalMappingHandlerProviders);
		this.globalErrorMappingHandlerProviders = globalErrorMappingHandlerProviders;
		Collections.sort(this.globalErrorMappingHandlerProviders);
		this.globalErrorHandlerProviders = globalErrorHandlerProviders;
		Collections.sort(this.globalErrorHandlerProviders);
		this.mapper = mapper;
		globalRunnableIndex = TreeMultimap.create();
		for (var provider : globalRunnableProviders)
			globalRunnableIndex.put(provider.getSignal(), provider);
	}

	public <T> Flux<T> enforceConstraintsOfDecisionOnResourceAccessPoint(AuthorizationDecision decision,
			Flux<T> resourceAccessPoint, Class<T> clazz) {
		var wrapped = resourceAccessPoint;
		wrapped = replaceIfResourcePresent(wrapped, decision.getResource(), clazz);
		wrapped = addConstraintHandlers(wrapped, decision.getObligations(), true, clazz);
		wrapped = addConstraintHandlers(wrapped, decision.getAdvice(), false, clazz);
		return wrapped;
	}

	private <T> Flux<T> replaceIfResourcePresent(Flux<T> resourceAccessPoint, Optional<JsonNode> resource,
			Class<T> clazz) {
		if (resource.isEmpty())
			return resourceAccessPoint;
		try {
			return Flux.just(unmarshallResource(resource.get(), clazz));
		} catch (JsonProcessingException | IllegalArgumentException e) {
			return Flux.error(new AccessDeniedException(
					String.format("Cannot map resource %s to type %s", resource.get().asText(), clazz.getSimpleName()),
					e));
		}
	}

	private <T> T unmarshallResource(JsonNode resource, Class<T> clazz)
			throws JsonProcessingException, IllegalArgumentException {
		return mapper.treeToValue(resource, clazz);
	}

	private <T> Flux<T> addConstraintHandlers(Flux<T> resourceAccessPoint, Optional<ArrayNode> constraints,
			boolean isObligation, Class<T> clazz) {
		var rap = resourceAccessPoint;
		if (constraints.isPresent())
			for (var constraint : constraints.get())
				rap = addConstraintHandlers(rap, constraint, isObligation, clazz);
		return rap;
	}

	private <T> Flux<T> addConstraintHandlers(Flux<T> rap, JsonNode constraint, boolean isObligation, Class<T> clazz) {
		var onDecisionHandlers = constructRunnableHandlersForConstraint(Signal.ON_DECISION, constraint, isObligation);
		var onCancelHandlers = constructRunnableHandlersForConstraint(Signal.ON_CANCEL, constraint, isObligation);
		var onCompleteHandlers = constructRunnableHandlersForConstraint(Signal.ON_COMPLETE, constraint, isObligation);
		var onTerminateHandlers = constructRunnableHandlersForConstraint(Signal.ON_TERMINATE, constraint, isObligation);
		var afterTerminateHandlers = constructRunnableHandlersForConstraint(Signal.AFTER_TERMINATE, constraint,
				isObligation);
		var onSubscribeHandlers = constructOnSubscribeHandlersForConstraint(constraint, isObligation);
		var onRequestHandlers = constructOnRequestHandlersForConstraint(constraint, isObligation);
		var doOnNextHandlers = constructConsumerHandlersForConstraint(constraint, isObligation, clazz);
		var onNextMapHandlers = constructMappingConstraintHandlersForConstraint(constraint, isObligation, clazz);

		var doOnErrorHandlers = constructDoOnErrorHandlersForConstraint(constraint, isObligation);
		var onErrorMapHandlers = constructErrorMappingConstraintHandlersForConstraint(constraint, isObligation);

		if (isObligation)
			if (onDecisionHandlers.size() + onCancelHandlers.size() + onCompleteHandlers.size()
					+ onTerminateHandlers.size() + afterTerminateHandlers.size() + doOnNextHandlers.size()
					+ onNextMapHandlers.size() + doOnErrorHandlers.size() + onErrorMapHandlers.size()
					+ onSubscribeHandlers.size() + onRequestHandlers.size() == 0)
				return Flux.error(new AccessDeniedException(
						String.format("No handler found for obligation: %s", constraint.asText())));

		var wrapped = rap;

		if (!onRequestHandlers.isEmpty())
			wrapped = wrapped.doOnRequest(consumeAllLong(onRequestHandlers));

		if (!onSubscribeHandlers.isEmpty())
			wrapped = wrapped.doOnSubscribe(consumeAll(onSubscribeHandlers));

		if (!onErrorMapHandlers.isEmpty())
			wrapped = wrapped.onErrorMap(mapAll(onErrorMapHandlers));

		if (!doOnErrorHandlers.isEmpty())
			wrapped = wrapped.doOnError(consumeAll(doOnErrorHandlers));

		if (!onNextMapHandlers.isEmpty())
			wrapped = wrapped.map(mapAll(onNextMapHandlers));

		if (!doOnNextHandlers.isEmpty())
			wrapped = wrapped.doOnNext(consumeAll(doOnNextHandlers));

		if (!onCancelHandlers.isEmpty())
			wrapped = wrapped.doOnCancel(runAll(onCancelHandlers));

		if (!onCompleteHandlers.isEmpty())
			wrapped = wrapped.doOnComplete(runAll(onCompleteHandlers));

		if (!onTerminateHandlers.isEmpty())
			wrapped = wrapped.doOnTerminate(runAll(onTerminateHandlers));

		if (!afterTerminateHandlers.isEmpty())
			wrapped = wrapped.doAfterTerminate(runAll(afterTerminateHandlers));

		if (!onDecisionHandlers.isEmpty())
			wrapped = onDecision(onDecisionHandlers).thenMany(wrapped);

		return wrapped;
	}

	private LongConsumer consumeAllLong(List<LongConsumer> handlers) {
		return value -> handlers.stream().forEach(handler -> handler.accept(value));
	}

	private List<Function<Throwable, Throwable>> constructErrorMappingConstraintHandlersForConstraint(
			JsonNode constraint, boolean isObligation) {
		return globalErrorMappingHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint))
				.map(failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(isObligation))
				.collect(Collectors.toList());
	}

	private <T> Function<T, T> mapAll(List<Function<T, T>> handlers) {
		return value -> handlers.stream()
				.reduce(Function.identity(), (merged, newFunction) -> x -> newFunction.apply(merged.apply(x)))
				.apply(value);
	}

	@SuppressWarnings("unchecked") // False positive the filter checks type
	private <T> List<Function<T, T>> constructMappingConstraintHandlersForConstraint(JsonNode constraint,
			boolean isObligation, Class<T> clazz) {
		return globalMappingHandlerProviders.stream().filter(provider -> provider.supports(clazz))
				.filter(provider -> provider.isResponsible(constraint))
				.map(provider -> (Function<T, T>) provider.getHandler(constraint))
				.map(failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(isObligation))
				.collect(Collectors.toList());
	}

	private <T> Consumer<T> consumeAll(List<Consumer<T>> handlers) {
		return value -> handlers.stream().forEach(handler -> handler.accept(value));
	}

	private List<Consumer<Subscription>> constructOnSubscribeHandlersForConstraint(JsonNode constraint,
			boolean isObligation) {
		return globalSubscriptionHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failConsumerOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
	}

	private List<LongConsumer> constructOnRequestHandlersForConstraint(JsonNode constraint, boolean isObligation) {

		return globalRequestHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint))
				.map(failLongConsumerOnlyIfObligationOrFatal(isObligation)).collect(Collectors.toList());
	}

	private List<Consumer<Throwable>> constructDoOnErrorHandlersForConstraint(JsonNode constraint,
			boolean isObligation) {
		return globalErrorHandlerProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failConsumerOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked") // False positive the filter checks type
	private <T> List<Consumer<T>> constructConsumerHandlersForConstraint(JsonNode constraint, boolean isObligation,
			Class<T> clazz) {
		return globalConsumerProviders.stream().filter(provider -> provider.supports(clazz))
				.filter(provider -> provider.isResponsible(constraint))
				.map(provider -> (Consumer<T>) provider.getHandler(constraint))
				.map(failConsumerOnlyIfObligationOrFatal(isObligation)).collect(Collectors.toList());
	}

	private Mono<Void> onDecision(List<Runnable> handlers) {
		return Mono.fromRunnable(runAll(handlers));
	}

	private Runnable runAll(List<Runnable> handlers) {
		return () -> handlers.stream().forEach(Runnable::run);
	}

	private List<Runnable> constructRunnableHandlersForConstraint(Signal signal, JsonNode constraint,
			boolean isObligation) {
		var potentialProviders = globalRunnableIndex.get(signal);
		var handlersForConstraint = potentialProviders.stream().filter(provider -> provider.isResponsible(constraint))
				.map(provider -> provider.getHandler(constraint)).map(failRunnableOnlyIfObligationOrFatal(isObligation))
				.collect(Collectors.toList());
		return handlersForConstraint;
	}

	private Function<Runnable, Runnable> failRunnableOnlyIfObligationOrFatal(boolean isObligation) {
		return runnable -> () -> {
			try {
				runnable.run();
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute runnable constraint handler", t);
			}
		};
	}

	private <T> Function<Consumer<T>, Consumer<T>> failConsumerOnlyIfObligationOrFatal(boolean isObligation) {
		return consumer -> value -> {
			try {
				consumer.accept(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute consumer constraint handler", t);
			}
		};
	}

	private <T> Function<LongConsumer, LongConsumer> failLongConsumerOnlyIfObligationOrFatal(boolean isObligation) {
		return consumer -> value -> {
			try {
				consumer.accept(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				// non-fatal will not be reported by Flux in doOnRequest -> Bubble to force
				// failure for an obligation
				if (isObligation)
					throw Exceptions
							.bubble(new AccessDeniedException("Failed to execute consumer constraint handler", t));
			}
		};
	}

	private <T> Function<Function<T, T>, Function<T, T>> failFunctionOnlyIfObligationOrFatalElseFallBackToIdentity(
			boolean isObligation) {
		return function -> value -> {
			try {
				return function.apply(value);
			} catch (Throwable t) {
				Exceptions.throwIfFatal(t);
				if (isObligation)
					throw new AccessDeniedException("Failed to execute consumer constraint handler", t);
				return value;
			}
		};
	}

}
