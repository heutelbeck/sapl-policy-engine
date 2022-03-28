/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.client.exception.RecoverableException;
import io.sapl.axon.constraints.AxonConstraintHandlerBundle;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 * The PolicyEnforcementPoint PEP intercepts actions taken by users within an
 * application. The PEP obtains a decision from the PolicyDecisionPoint and lets
 * either the application process or denies access. According to the Command
 * Query Response Pattern of Axon IQ the PEP has different Methods to handle
 * Command Messages, Single Query Messages and Subscription Query Messages.
 */

@Service
@RequiredArgsConstructor
public class QueryPolicyEnforcementPoint {

	private final PolicyDecisionPoint                     pdp;
	private final AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService;
	private final ConstraintHandlerService                constraintHandlerService;
	private final ObjectMapper                            mapper;

	// for axon update messages
	private final ConcurrentHashMap<String, Disposable> decisionsDisposable = new ConcurrentHashMap<>();

	// for axon point-to-point messages
	private final ConcurrentHashMap<String, AuthorizationDecision> currentDecisions   = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Annotation>            currentAnnotations = new ConcurrentHashMap<>();
	private final Set<String>                                      unenforcedMessages = new HashSet<>();
	private static final String                                    DENY               = "Denied by PDP";
	// important for Axon-Server usage
	private final ConcurrentHashMap<String, SubscriptionQueryMessage<?, ?, ?>> currentSubscriptionQueryMessages = new ConcurrentHashMap<>();

	/**
	 * Executes the Method getAuthorizationDecisionFuture to get the Authorization
	 * Decision from the Policy Decision Point, before the query is executed
	 * asynchronously. It completes either with an AccessDeniedException or calls
	 * the Method handlePayloadConstraints.
	 *
	 * @param <Q>                  PayloadType of the Query
	 * @param <R>                  expected ResultType of the handled Query
	 * @param <U>                  messageType
	 * @param queryMessage         Representation of a QueryMessage
	 * @param preEnforceAnnotation Annotation to intercept the Query before
	 *                             execution
	 * @param executable           an execuatable
	 * @return completableFuture, containing the ConstraintHandlerBundle and the
	 *         Message with executed ConstraintHandler for the message
	 */
	public <Q, R, U extends QueryMessage<Q, R>>
			CompletableFuture<Tuple3<AxonConstraintHandlerBundle<Q, R, U>, U, Optional<JsonNode>>>
			preEnforceSingleQuery(
					U queryMessage,
					Annotation preEnforceAnnotation,
					Executable executable) {
		var authorizationDecisionCompletableFuture = getAuthorizationDecisionCompletableFuture(queryMessage,
				preEnforceAnnotation, executable, null);

		return authorizationDecisionCompletableFuture.thenApply(authorizationDecision -> {
			checkPermission(authorizationDecision);
			var bundle        = createQueryBundle(authorizationDecision, queryMessage);
			var mappedMessage = executeMessageHandlerProvider(bundle, queryMessage);

			return Tuples.of(bundle, mappedMessage, authorizationDecision.getResource());
		});
	}

	/**
	 * Executes the Method getAuthorizationDecisionFuture to get the Authorization
	 * Decision from the Policy Decision Point, after the command is executed
	 * asynchronously. It completes either with an AccessDeniedException or calls
	 * the Method handleResultConstraints.
	 *
	 * @param <Q>                   PayloadType of the Query
	 * @param <R>                   expected ResultType of the handled Query
	 * @param queryMessage          Representation of a QueryMessage
	 * @param handleObj             Result of the handled Query
	 * @param postEnforceAnnotation Annotation to intercept the Query after
	 *                              execution
	 * @param executable            an executable
	 * @return completableFuture, containing the ConstraintHandlerBundle and the
	 *         Message with executed ConstraintHandler for the message
	 */
	public <Q, R> CompletableFuture<R> postEnforceSingleQuery(
			QueryMessage<Q, R> queryMessage,
			R handleObj,
			Annotation postEnforceAnnotation,
			Executable executable) {
		var authorizationDecisionCompletableFuture = getAuthorizationDecisionCompletableFuture(queryMessage,
				postEnforceAnnotation, executable, handleObj);

		return authorizationDecisionCompletableFuture.thenApply(authorizationDecision -> {

			checkPermission(authorizationDecision);
			var bundle = createQueryBundle(authorizationDecision, queryMessage);

			return executeResultHandling(handleObj, bundle, authorizationDecision.getResource(),
					queryMessage.getResponseType().responseMessagePayloadType());

		});
	}

	/**
	 * Executes the Method getAuthorizationDecisionFuture to get the Authorization
	 * Decision from the Policy Decision Point. Depending on the type of
	 * Subscription Annotation and the decision it completes either with an
	 * AccessDeniedException or calls the Method handlePayloadConstraints.
	 *
	 * @param <Q>          PayloadType of the Query
	 * @param <R>          expected ResultType of the handled Query
	 * @param <U>          messageType
	 * @param queryMessage Representation of a QueryMessage
	 * @param annotation   the kind of annotation
	 * @param executable   an executable
	 * @return completableFuture, containing the ConstraintHandlerBundle and the
	 *         Message with executed ConstraintHandler for the message
	 */
	public <Q, R, U extends SubscriptionQueryMessage<Q, R, ?>>
			CompletableFuture<Tuple3<AxonConstraintHandlerBundle<Q, R, U>, U, Optional<JsonNode>>>
			enforceSubscriptionQuery(
					U queryMessage,
					Annotation annotation,
					Executable executable) {
		var authorizationDecisionFlux    = getAuthorizationDecisionFlux(queryMessage, annotation, executable, null);
		var initialAuthorizationDecision = authorizationDecisionFlux.next().toFuture();
		registerDecision(queryMessage.getIdentifier(), authorizationDecisionFlux, annotation);

		var authorizationDecisionCompletableFuture = new CompletableFuture<AuthorizationDecision>();

		initialAuthorizationDecision.exceptionally(exception -> {
			removeSubscription(queryMessage.getIdentifier());
			authorizationDecisionCompletableFuture.completeExceptionally(new AccessDeniedException("Denied by PDP"));
			return null;
		});

		authorizationDecisionCompletableFuture.exceptionally(exception -> {
			removeSubscription(queryMessage.getIdentifier());
			return null;
		});

		waitUntilPermit(authorizationDecisionFlux, initialAuthorizationDecision, authorizationDecisionCompletableFuture,
				annotation);
		return authorizationDecisionCompletableFuture.thenApply(authorizationDecision -> {
			var bundle        = createQueryBundle(authorizationDecision, queryMessage);
			var mappedMessage = executeMessageHandlerProvider(bundle, queryMessage);
			return Tuples.of(bundle, mappedMessage, authorizationDecision.getResource());
		});
	}

	private void waitUntilPermit(
			Flux<AuthorizationDecision> authorizationDecisionFlux,
			CompletableFuture<AuthorizationDecision> initialAuthorizationDecision,
			CompletableFuture<AuthorizationDecision> authorizationDecisionCompletableFuture,
			Annotation annotation) {

		initialAuthorizationDecision.thenAccept(authorizationDecision -> {
			if (authorizationDecision == null) {
				authorizationDecisionCompletableFuture.completeExceptionally(new AccessDeniedException(DENY));
				return;
			}
			if (authorizationDecision.getDecision() == Decision.PERMIT)
				authorizationDecisionCompletableFuture.complete(authorizationDecision);
			else if (annotation.annotationType().isAssignableFrom(EnforceTillDenied.class)) {
				authorizationDecisionCompletableFuture.completeExceptionally(new AccessDeniedException(DENY));
			} else
				authorizationDecisionFlux
						.doOnError(exception -> authorizationDecisionCompletableFuture
								.completeExceptionally(new AccessDeniedException(DENY)))
						.subscribe(nextDecision -> returnWhenFutureCompleted(authorizationDecisionCompletableFuture,
								nextDecision));
		});
	}

	private void returnWhenFutureCompleted(
			CompletableFuture<AuthorizationDecision> authorizationDecisionCompletableFuture,
			AuthorizationDecision nextDecision) {
		if (authorizationDecisionCompletableFuture.isDone())
			return;
		if (nextDecision.getDecision() == Decision.PERMIT)
			authorizationDecisionCompletableFuture.complete(nextDecision);
	}

	/**
	 * Add a MessageIdentifier to a List which contains all unenforced messages.
	 *
	 * SubscriptionQueryMessages, which are not enforced need to be added to the
	 * unenforcedMessages List. Updates for messages, which have no Annotation and
	 * are not listed as unenforcedMessages are dropped without notice.
	 *
	 * @param stringIdentifier Unique Identifier of a Message
	 */
	public void addUnenforcedMessage(String stringIdentifier) {
		unenforcedMessages.add(stringIdentifier);
	}

	/**
	 * handles SubscriptionQueryUpdateMessages. In case there is an annotation and
	 * the decision is not null, the Method createQueryBundle from the
	 * constraintHandlerService is called to get the result. The result is then
	 * returned as an updateMessage.
	 *
	 * @param <Q>          PayloadType of the Query
	 * @param <U>          SubscriptionQueryMessageType
	 * @param queryMessage Representation of a QueryMessage
	 * @param update       Update of the queryMessage
	 * @return Message which holds incremental update of a subscription query
	 * @throws Exception on error
	 */
	public <Q, U> SubscriptionQueryUpdateMessage<U> handleSubscriptionQueryUpdateMessage(
			Message<Q> queryMessage,
			SubscriptionQueryUpdateMessage<U> update)
			throws Exception {
		var currentAnnotation = currentAnnotations.get(queryMessage.getIdentifier());

		if (currentAnnotation == null)
			return unenforcedMessages.contains(queryMessage.getIdentifier()) ? update : null;

		var currentDecision = currentDecisions.get(queryMessage.getIdentifier());
		if (currentDecision == null)
			return null;
		if (currentDecision.getDecision() != Decision.PERMIT) {
			if (currentAnnotation instanceof EnforceDropWhileDenied)
				return null;
			if (currentAnnotation instanceof EnforceRecoverableIfDenied)
				throw new RecoverableException();
			throw new AccessDeniedException("DENIED");

		}
		U   payloadCopy = mapper.readValue(mapper.writeValueAsString(update.getPayload()), update.getPayloadType());
		U   newResource = replaceIfResourcePresent(payloadCopy, currentDecision.getResource(), update.getPayloadType());
		var bundle      = constraintHandlerService.createQueryBundle(currentDecision, queryMessage,
				ResponseTypes.instanceOf(update.getPayloadType()));

		var result = bundle.executeResultHandlerProvider(newResource);
		return GenericSubscriptionQueryUpdateMessage.asUpdateMessage(result);
	}

	private void registerDecision(
			String messageIdentifier,
			Flux<AuthorizationDecision> authorizationDecisionFlux,
			Annotation enforceAnnotation) {
		currentAnnotations.put(messageIdentifier, enforceAnnotation);

		var decisionFluxDisposable = authorizationDecisionFlux.doFinally(signal -> {
			if (signal == SignalType.ON_COMPLETE)
				disposeDecision(messageIdentifier);
			else
				removeSubscription(messageIdentifier);
		}).doOnNext(updateDecision(messageIdentifier)).subscribe(updateDecision(messageIdentifier));
		AddDecisionFluxDisposableToMap(messageIdentifier, decisionFluxDisposable);
	}

	private Consumer<AuthorizationDecision> updateDecision(String messageIdentifier) {
		return decision -> currentDecisions.put(messageIdentifier, decision);
	}

	private void AddDecisionFluxDisposableToMap(String messageIdentifier, Disposable decisionFluxDisposable) {
		decisionsDisposable.put(messageIdentifier, decisionFluxDisposable);
	}

	/**
	 * Removes the currentAnnotations and the currentDecisions from the map.
	 *
	 * @param messageIdentifier the key that needs to be removed
	 */
	public void removeSubscription(String messageIdentifier) {
		currentAnnotations.remove(messageIdentifier);
		currentDecisions.remove(messageIdentifier);
		unenforcedMessages.remove(messageIdentifier);
		currentSubscriptionQueryMessages.remove(messageIdentifier);
		disposeDecision(messageIdentifier);
	}

	private void disposeDecision(String messageIdentifier) {
		var decisionDisposable = decisionsDisposable.remove(messageIdentifier);
		if (decisionDisposable != null) {
			decisionDisposable.dispose();
		}
	}

	private Flux<AuthorizationDecision> getAuthorizationDecisionFlux(
			QueryMessage<?, ?> queryMessage,
			Annotation annotation,
			Executable executable,
			Object resultObject) {
		var authorizationSubscription = authorizationSubscriptionBuilderService
				.constructAuthorizationSubscriptionForQuery(queryMessage, annotation, executable,
						Optional.ofNullable(resultObject));
		return pdp.decide(authorizationSubscription);
	}

	private <Q, R> CompletableFuture<AuthorizationDecision> getAuthorizationDecisionCompletableFuture(
			QueryMessage<Q, R> queryMessage,
			Annotation preEnforceAnnotation,
			Executable executable,
			Object resultObject) {
		return CompletableFuture.supplyAsync(() -> null)
				.thenCompose(emptyFuture -> getAuthorizationDecisionFlux(queryMessage, preEnforceAnnotation, executable,
						resultObject)
								.next().toFuture());
	}

	private void checkPermission(AuthorizationDecision authorizationDecision) {
		if (authorizationDecision == null || (authorizationDecision.getDecision() != Decision.PERMIT))
			throw new AccessDeniedException(DENY);
	}

	private <Q, R, U extends QueryMessage<Q, R>> AxonConstraintHandlerBundle<Q, R, U> createQueryBundle(
			AuthorizationDecision authorizationDecision,
			U queryMessage) {
		return constraintHandlerService.createQueryBundle(authorizationDecision, queryMessage,
				queryMessage.getResponseType());
	}

	private <Q, R, M extends Message<Q>> M executeMessageHandlerProvider(
			AxonConstraintHandlerBundle<Q, R, M> bundle,
			M queryMessage) {
		return bundle.executeMessageHandlers(queryMessage);

	}

	/**
	 * Executes HandlerProviders responsible for the Result
	 *
	 * @param <Q>          PayloadType of the Query
	 * @param <R>          expected ResultType of the handled Query
	 * @param <M>          messageType
	 * @param handleObject Result of the handled Query
	 * @param bundle       ConstraintHandlerBundle which contains all
	 *                     ConstraintHandlerProvider responsible for this Query
	 *                     before and after the Query is handled
	 * @param newResource  the new resource
	 * @param clazz        the type of the result
	 * @return Result mapped with responsible ConstraintHandlerProvider
	 */
	public <Q, R, M extends Message<Q>> R executeResultHandling(
			R handleObject,
			AxonConstraintHandlerBundle<Q, R, M> bundle,
			Optional<JsonNode> newResource,
			Class<R> clazz) {
		if (newResource.isPresent())
			return bundle.executeResultHandlerProvider(replaceIfResourcePresent(handleObject, newResource, clazz));
		return bundle.executeResultHandlerProvider(handleObject);
	}

	private <T> T replaceIfResourcePresent(T resourceOld, Optional<JsonNode> resource, Class<T> clazz) {
		if (resource.isEmpty())
			return resourceOld;
		try {
			if (resource.get().has("queryResult")) {
				return unmarshallResource(resource.get().get("queryResult"), clazz);
			}
			return resourceOld;
		} catch (JsonProcessingException | IllegalArgumentException e) {
			throw new AccessDeniedException(
					String.format("Cannot map resource %s to type %s", resource.get().asText(), clazz.getSimpleName()),
					e);
		}
	}

	private <T> T unmarshallResource(JsonNode resource, Class<T> clazz) throws JsonProcessingException {
		return mapper.treeToValue(resource, clazz);
	}

	/**
	 * Adds subscriptionQueryMessage to a Map that the QueryHandlingMember could use
	 * it. The key of the Map is the message identifier.
	 * 
	 * @param subscriptionQueryMessage Representation of a SubscriptionQueryMessage
	 */
	public void addSubscriptionQueryMessage(SubscriptionQueryMessage<?, ?, ?> subscriptionQueryMessage) {
		currentSubscriptionQueryMessages.putIfAbsent(subscriptionQueryMessage.getIdentifier(),
				subscriptionQueryMessage);
	}

	/**
	 * Returns the SubscriptionQueryMessage for the given message identifier.
	 * 
	 * @param messageIdentifier Unique Identifier of the SubscriptionQueryMessage
	 * @return the SubscriptionQueryMessage which is looking for
	 */
	public SubscriptionQueryMessage<?, ?, ?> getCurrentSubscriptionQueryMessage(String messageIdentifier) {
		return currentSubscriptionQueryMessages.get(messageIdentifier);
	}

}
