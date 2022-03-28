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

package io.sapl.axon.async;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;

import io.sapl.axon.queryhandling.QueryPolicyEnforcementPoint;
import io.sapl.axon.utilities.Annotations;
import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Abstract implementation of a {@link MessageHandlingMember} that delegates
 * Queries to a wrapped MessageHandlingMember. Inside this abstract
 * implementation the {@link QueryPolicyEnforcementPoint} is wrapped in a
 * CompletableFuture to handle the authorization of a single
 * {@link QueryMessage} and initial queries of {@link SubscriptionQueryMessage}.
 * {@link PreEnforce} and {@link PostEnforce} annotations on the QueryHandler
 * will be enforced and the Constraints will be handled before and after
 * QueryHandling. {@link io.sapl.spring.method.metadata.EnforceTillDenied},
 * {@link EnforceDropWhileDenied} and
 * {@link EnforceRecoverableIfDenied} annotations for SubscriptionQueries
 * will be enforced and the Constraints will be handled for the initial query.
 * Extend this class to provide additional functionality to the delegate member.
 *
 */

abstract public class AbstractSAPLQueryHandlingMember<T> extends WrappedMessageHandlingMember<T> {

	private final MessageHandlingMember<T> delegate;
	private final QueryPolicyEnforcementPoint pep;

	protected AbstractSAPLQueryHandlingMember(MessageHandlingMember<T> delegate, QueryPolicyEnforcementPoint pep) {
		super(delegate);
		this.delegate = delegate;
		this.pep = pep;
	}

	/**
	 * Distinguishes between SingleQueryMessage and SubscriptionQueryMessage and
	 * returns the appropriate function to handle the Query. Throws Exception in case
	 * of no QueryMessage.
	 */
	@Override
	public Object handle(Message<?> message, T source) throws Exception {
		// Check if the Message is a QueryMessage
		if (!(message instanceof QueryMessage<?, ?>))
			throw new Exception("Message must be a QueryMessage");
		var executable = delegate.unwrap(Executable.class).orElseThrow();

		var existingSubscriptionQueryMessage = pep.getCurrentSubscriptionQueryMessage(message.getIdentifier());
		if (Objects.nonNull(existingSubscriptionQueryMessage))
			return handleSubscriptionQuery(existingSubscriptionQueryMessage, source, executable);
		return handleQuery((QueryMessage<?, ?>) message, source, executable);
	}

	@SuppressWarnings("unchecked")
	//Cast to R, because R is the ReturnType of a QueryMessage and super.handle executes the Query and returns the result
	private <R> R handleQueryStage(QueryMessage<?, R> message, T source) throws QueryExecutionException {
		try {
			return (R) super.handle(message, source);
		} catch (Exception e) {
			throw new QueryExecutionException("Exception Handler: ", e);
		}
	}

	private <Q, R, U extends SubscriptionQueryMessage<Q, R, ?>> Object handleSubscriptionQuery(U message, T source,
			Executable executable) throws Exception {
		var annotation = getSubscriptionQueryAnnotations(executable.getDeclaredAnnotations());
		if (annotation == null) {
			pep.addUnenforcedMessage(message.getIdentifier());
			return handleQueryStage(message, source);
		}

		// enforceSubscriptionQuery handles Enforce and 1st ConstraintHandlingPhase,
		// returns Tuple
		// Handle Query, returns Future Object
		// Enforce Constraints on Result, returns Future Object

		var preHandleStage = pep.enforceSubscriptionQuery(message, annotation, executable);

		CompletableFuture<R> handleStage = preHandleStage.thenApply(tuple -> handleQueryStage(tuple.getT2(), source));

		return handleStage.thenCombine(preHandleStage, (object, tuple) -> pep.executeResultHandling(object,
				tuple.getT1(), tuple.getT3(), message.getResponseType().responseMessagePayloadType()));

	}

	private <Q, R, U extends QueryMessage<Q, R>> Object handleQuery(U message, T source, Executable executable) {

		var annotations = getSingleQueryAnnotations(executable.getDeclaredAnnotations());
		if (annotations.isEmpty())
			return handleQueryStage(message, source);

		// PreEnforceQuery handles Enforce and 1st ConstraintHandlingPhase, returns
		// Tuple
		// Handle Query, returns Future Object
		// Enforce Constraints on Result, returns Future Object
		// Handle PostEnforce and enforce Constraints returns Future Object
		CompletableFuture<R> handleObjFuture;

		var preEnforceAnnotation = annotations.stream()
				.filter(annotation -> annotation.annotationType().isAssignableFrom(PreEnforce.class)).findFirst();
		var postEnforcedAnnotation = annotations.stream()
				.filter(annotation -> annotation.annotationType().isAssignableFrom(PostEnforce.class)).findFirst();

		if (preEnforceAnnotation.isEmpty()) {
			handleObjFuture = CompletableFuture.supplyAsync(() -> handleQueryStage(message, source));
		} else {
			var preEnforceStage = pep.preEnforceSingleQuery(message, preEnforceAnnotation.get(), executable);

			CompletableFuture<R> handleStage = preEnforceStage
					.thenApply(tuple -> handleQueryStage(tuple.getT2(), source));

			handleObjFuture = handleStage.thenCombine(preEnforceStage,
					(object, tuple2) -> pep.executeResultHandling(object, tuple2.getT1(), tuple2.getT3(),
							message.getResponseType().responseMessagePayloadType()));

			if (postEnforcedAnnotation.isEmpty())
				return handleObjFuture;
			CompletableFuture<Tuple2<U, R>> combineFuturesForCompose = handleObjFuture.thenCombine(preEnforceStage,
					(handleObj, tuple) -> Tuples.of(tuple.getT2(), handleObj));
			return combineFuturesForCompose.thenCompose(tuple2 -> pep.postEnforceSingleQuery(tuple2.getT1(),
					tuple2.getT2(), postEnforcedAnnotation.get(), executable));
		}

		return handleObjFuture.thenCompose(handleObj -> pep.postEnforceSingleQuery(message, handleObj,
				postEnforcedAnnotation.orElseThrow(), executable));
	}

	private Annotation getSubscriptionQueryAnnotations(Annotation[] annotations) throws Exception {

		Annotation annotation = null;
		for (var currentAnnotation : annotations)
			for (var clazz : Annotations.SUBSCRIPTION_ANNOTATIONS)
				if (clazz.isAssignableFrom(currentAnnotation.getClass())) {
					if (annotation != null)
						throw new Exception("Multiple Subscription Annotations are not allowed");
					annotation = currentAnnotation;
				}
		return annotation;
	}

	private List<Annotation> getSingleQueryAnnotations(Annotation[] annotations) {
		List<Annotation> annotationList = new LinkedList<>();
		for (var currentAnnotation : annotations)
			for (var clazz : Annotations.SINGLE_ANNOTATIONS)
				if (clazz.isAssignableFrom(currentAnnotation.getClass()))
					annotationList.add(currentAnnotation);
		return annotationList;
	}

}
