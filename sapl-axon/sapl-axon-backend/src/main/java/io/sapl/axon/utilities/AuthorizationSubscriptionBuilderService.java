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

package io.sapl.axon.utilities;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.RequiredArgsConstructor;

/**
 * The AuthorizationSubscriptionBuilderService Object offers methods to get the
 * AuthorizationSubscription for Query Messages and for Command Messages.
 */

@Service
@RequiredArgsConstructor
public class AuthorizationSubscriptionBuilderService {

	private static final String ENVIRONMENT = "environment";
	private static final String RESOURCE    = "resource";
	private static final String ACTION      = "action";
	private static final String SUBJECT     = "subject";
	private final ObjectMapper  mapper;

	/**
	 * Executed to get the AuthorizationSubscription for a Query Message. It gets
	 * the queryMessage and the annotation and returns the constructed
	 * AuthorizationSubscription.
	 * 
	 * @param queryMessage Representation of a QueryMessage, containing a Payload
	 *                     and MetaData
	 * @param annotation   Annotation from the respective method
	 * @param executable   an execuatable
	 * @param queryResult  a query result
	 * @return the AuthorizationSubscription for the Query
	 */
	public AuthorizationSubscription constructAuthorizationSubscriptionForQuery(
			QueryMessage<?, ?> queryMessage,
			Annotation annotation,
			Executable executable,
			Optional<?> queryResult) {

		var annotationAttributeValues = retrieveAttributeValuesFromAnnotation(annotation);

		var subject     = retrieveSubject(queryMessage, annotationAttributeValues);
		var action      = retrieveAction(queryMessage, annotationAttributeValues);
		var resource    = retrieveResourceFromQueryMessage(queryMessage, annotationAttributeValues, executable,
				queryResult);
		var environment = retrieveEnvironment(annotationAttributeValues);

		return AuthorizationSubscription.of(subject, action, resource, environment);
	}

	/**
	 * Executed to get the AuthorizationSubscription for a Command Message. It gets
	 * the message, target and the delegate and returns the constructed
	 * AuthorizationSubscription.
	 * 
	 * @param message   Representation of a Message, containing a Payload and
	 *                  MetaData
	 * @param aggregate Object, which contains state and methods to alter that state
	 * @param delegate  Command handler, needed to set parameter from annotation
	 * @return the AuthorizationSubscription for the Command
	 */

	public AuthorizationSubscription constructAuthorizationSubscriptionForCommand(
			CommandMessage<?> message,
			Object aggregate,
			MessageHandlingMember<?> delegate) {

		Annotation methodAnnotation          = retrievePreEnforceAnnotation(delegate);
		var        annotationAttributeValues = retrieveAttributeValuesFromAnnotation(methodAnnotation);

		var subject     = retrieveSubject(message, annotationAttributeValues);
		var action      = retrieveAction(message, annotationAttributeValues);
		var resource    = retrieveResourceFromTarget(message, aggregate, annotationAttributeValues);
		var environment = retrieveEnvironment(annotationAttributeValues);

		return AuthorizationSubscription.of(subject, action, resource, environment);
	}

	private Map<String, Object> retrieveAttributeValuesFromAnnotation(Annotation annotation) {
		return AnnotationUtils.getAnnotationAttributes(annotation);
	}

	private JsonNode retrieveSubject(Message<?> message, Map<String, Object> values) {
		var subjectNode = mapper.createObjectNode();
		var subject     = values.get(SUBJECT).toString();

		if (!subject.isBlank()) {
			var expr = setUpExpression(subject).getValue();
			if (expr != null)
				return subjectNode.put("name", expr.toString());
		}

		if (message.getMetaData().get(SUBJECT) == null)
			return null;

		return subjectNode.setAll((ObjectNode) message.getMetaData().get(SUBJECT));
	}

	private JsonNode retrieveAction(Message<?> message, Map<String, Object> values) {
		var actionAnnotation = values.get(ACTION).toString();
		var actionNode       = mapper.createObjectNode();

		if (!actionAnnotation.isBlank())
			actionNode.set(actionAnnotation, evaluateSpEL(message.getPayload(), actionAnnotation));

		constructActionWithMessage(message, actionNode);
		return actionNode;
	}

	private void constructActionWithMessage(Message<?> message, ObjectNode actionNode) {
		actionNode.put("name", message.getPayloadType().getSimpleName());
		actionNode.set("message", mapper.valueToTree(message.getPayload()));
		actionNode.set("metadata", mapper.valueToTree(message.getMetaData()));
	}

	private JsonNode retrieveResourceFromQueryMessage(
			QueryMessage<?, ?> message,
			Map<String, Object> values,
			Executable executable,
			Optional<?> queryResult) {
		var resourceNode       = mapper.createObjectNode();
		var resourceAnnotation = values.get(RESOURCE).toString();

		if (!resourceAnnotation.isBlank()) {
			var expr = setUpExpression(resourceAnnotation).getValue();
			if (expr != null)
				resourceNode.put(RESOURCE, expr.toString());
		}

		queryResult.ifPresent(result -> resourceNode.set("queryResult", mapper.valueToTree(result)));

		resourceNode.put("projectionClass", executable.getDeclaringClass().getSimpleName());
		resourceNode.put("methodName", executable.getName());
		resourceNode.put("responsetype", message.getResponseType().getExpectedResponseType().getSimpleName());
		resourceNode.put("queryname", message.getPayloadType().getSimpleName());

		if (message.getPayloadType().getEnclosingClass() != null)
			resourceNode.put("classname", message.getPayloadType().getEnclosingClass().getSimpleName());

		return resourceNode;
	}

	private Object retrieveEnvironment(Map<String, Object> values) {
		var env = values.get(ENVIRONMENT).toString();
		if (env.isBlank())
			return null;

		var expr = setUpExpression(env).getValue();
		if (expr == null)
			return null;

		return expr.toString();

	}

	private <T> Annotation retrievePreEnforceAnnotation(MessageHandlingMember<T> handler) {
		var method = handler.unwrap(Executable.class).orElseThrow();
		return AnnotationUtils.getAnnotation(method, PreEnforce.class);
	}

	private <T> JsonNode retrieveResourceFromTarget(Message<?> message, T aggregate, Map<String, Object> values) {
		var resourceNode       = mapper.createObjectNode();
		var resourceAnnotation = values.get(RESOURCE).toString();

		if (!resourceAnnotation.isBlank())
			resourceNode.set(resourceAnnotation, evaluateSpEL(aggregate, resourceAnnotation));

		constructResourceWithAggregateInformation(message, aggregate, resourceNode);

		return resourceNode;
	}

	private <T> void constructResourceWithAggregateInformation(
			Message<?> message,
			T aggregate,
			ObjectNode resourceNode) {

		var aggregateType = message.getMetaData().get(Constants.aggregateType.name());
		if (aggregateType != null)
			resourceNode.set(Constants.aggregateType.name(), mapper.valueToTree(aggregateType));
		else if (aggregate != null)
			if (aggregate.getClass().isAnnotationPresent(Aggregate.class))
				resourceNode.put(Constants.aggregateType.name(), aggregate.getClass().getSimpleName());

		var fields = message.getPayloadType().getDeclaredFields();
		for (var field : fields) {
			if (field.isAnnotationPresent(TargetAggregateIdentifier.class)) {
				resourceNode.set(Constants.aggregateIdentifier.name(),
						evaluateSpEL(message.getPayload(), field.getName()));
				break;
			}
		}
	}

	private JsonNode evaluateToJson(Expression expr, StandardEvaluationContext ctx) {
		try {
			return mapper.valueToTree(expr.getValue(ctx));
		} catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	private StandardEvaluationContext setUpEvaluationContext(Object object) {
		return new StandardEvaluationContext(object);
	}

	private Expression setUpExpression(String expr) {
		ExpressionParser expressionParser = new SpelExpressionParser();
		return expressionParser.parseExpression(expr);
	}

	private JsonNode evaluateSpEL(Object obj, String expr) {
		var context    = setUpEvaluationContext(obj);
		var expression = setUpExpression(expr);
		return evaluateToJson(expression, context);

	}
}
