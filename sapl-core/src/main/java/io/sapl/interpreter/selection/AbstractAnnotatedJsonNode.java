/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * Represents an annotated JsonNode in a selection result tree.
 */
@Data
@AllArgsConstructor
public abstract class AbstractAnnotatedJsonNode implements ResultNode {

	private static final String UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION = "Undefined values handed over to function evaluation.";
	private static final String FILTER_FUNCTION_EVALUATION = "Custom filter function '%s' could not be evaluated.";
	private static final String FILTER_EACH_NO_ARRAY = "Trying to filter each element of array, but got type %s.";
	protected static final String UNDEFINED_VALUES_CANNOT_BE_ADDED_TO_RESULTS_IN_JSON_FORMAT = "Undefined values cannot be added to results in JSON format.";

	protected Optional<JsonNode> node;
	protected Optional<JsonNode> parent;

	public AbstractAnnotatedJsonNode(Optional<JsonNode> node) {
		this.node = node;
		this.parent = Optional.empty();
	}

	@Override
	public Optional<JsonNode> asJsonWithoutAnnotations() {
		return node;
	}

	@Override
	public boolean isResultArray() {
		return false;
	}

	/**
	 * Removes each item from a JsonNode.
	 *
	 * @param parentNode the parent JsonNode node
	 * @throws PolicyEvaluationException in case the parentNode is no array
	 */
	protected static void removeEachItem(Optional<JsonNode> parentNode) throws PolicyEvaluationException {
		if (!parentNode.isPresent() || !parentNode.get().isArray()) {
			throw new PolicyEvaluationException(String.format(FILTER_EACH_NO_ARRAY,
					parentNode.isPresent() ? parentNode.get().getNodeType() : "undefined"));
		} else {
			((ArrayNode) parentNode.get()).removeAll();
		}
	}

	/**
	 * Replaces each item of an array by the result of evaluating a filter function
	 * for the item.
	 *
	 * @param function   name of the filter function
	 * @param parentNode the parent JsonNode (must be an array node)
	 * @param arguments  arguments to be passed to the function as a JSON array
	 * @param ctx        the evaluation context
	 * @param isBody     true if the expression occurs within the policy body
	 *                   (attribute finder steps are only allowed if set to true)
	 * @return a flux of {@link ResultNode.Void} instances, each indicating a
	 *         finished application of the function to each item of the parent array
	 *         node
	 * @throws PolicyEvaluationException
	 */
	@SuppressWarnings("unchecked")
	protected static Flux<Void> applyFilterToEachItem(String function, Optional<JsonNode> parentNode,
			Arguments arguments, EvaluationContext ctx, boolean isBody) {
		if (!parentNode.isPresent() || !parentNode.get().isArray()) {
			throw Exceptions.propagate(new PolicyEvaluationException(String.format(FILTER_EACH_NO_ARRAY,
					parentNode.isPresent() ? parentNode.get().getNodeType() : "undefined")));
		}

		final ArrayNode arrayNode = (ArrayNode) parentNode.get();

		final String fullyQualifiedName = ctx.getImports().getOrDefault(function, function);
		if (arguments != null && !arguments.getArgs().isEmpty()) {
			final List<Flux<Optional<JsonNode>>> parameterFluxes = new ArrayList<>(arguments.getArgs().size());
			for (Expression argument : arguments.getArgs()) {
				parameterFluxes.add(argument.evaluate(ctx, isBody, parentNode));
			}
			return Flux.combineLatest(parameterFluxes, paramNodes -> {
				for (int i = 0; i < arrayNode.size(); i++) {
					final JsonNode childNode = arrayNode.get(i);
					final ArrayNode argumentsArray = JsonNodeFactory.instance.arrayNode();
					argumentsArray.add(childNode);
					for (Object paramNode : paramNodes) {
						argumentsArray.add(((Optional<JsonNode>) paramNode).orElseThrow(() -> Exceptions.propagate(
								new PolicyEvaluationException(UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION))));
					}
					try {
						final Optional<JsonNode> modifiedChildNode = ctx.getFunctionCtx().evaluate(fullyQualifiedName,
								argumentsArray);
						arrayNode.set(i,
								modifiedChildNode.orElseThrow(() -> Exceptions.propagate(new PolicyEvaluationException(
										UNDEFINED_VALUES_CANNOT_BE_ADDED_TO_RESULTS_IN_JSON_FORMAT))));
					} catch (FunctionException e) {
						throw Exceptions.propagate(
								new PolicyEvaluationException(String.format(FILTER_FUNCTION_EVALUATION, function), e));
					}
				}
				return ResultNode.Void.INSTANCE;
			}).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
		} else {
			try {
				for (int i = 0; i < arrayNode.size(); i++) {
					final JsonNode childNode = arrayNode.get(i);
					final ArrayNode argumentsArray = JsonNodeFactory.instance.arrayNode();
					argumentsArray.add(childNode);
					final Optional<JsonNode> modifiedChildNode = ctx.getFunctionCtx().evaluate(fullyQualifiedName,
							argumentsArray);
					arrayNode.set(i,
							modifiedChildNode.orElseThrow(() -> Exceptions.propagate(new PolicyEvaluationException(
									UNDEFINED_VALUES_CANNOT_BE_ADDED_TO_RESULTS_IN_JSON_FORMAT))));
				}
				return Flux.just(ResultNode.Void.INSTANCE);
			} catch (FunctionException e) {
				return Flux
						.error(new PolicyEvaluationException(String.format(FILTER_FUNCTION_EVALUATION, function), e));
			}
		}
	}

	/**
	 * Applies a function to a JSON node and returns a flux of the results.
	 *
	 * @param function     the name of the function
	 * @param node         the JSON node to apply the function to
	 * @param arguments    other arguments to be passed to the function as a JSON
	 *                     array
	 * @param ctx          the evaluation context
	 * @param isBody       true if the expression occurs within the policy body
	 *                     (attribute finder steps are only allowed if set to true)
	 * @param relativeNode the node a relative expression evaluates to
	 * @return a flux of the results as JsonNodes
	 */
	@SuppressWarnings("unchecked")
	public static Flux<Optional<JsonNode>> applyFilterToNode(String function, Optional<JsonNode> node,
			Arguments arguments, EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		final String fullyQualifiedName = ctx.getImports().getOrDefault(function, function);
		if (arguments != null && !arguments.getArgs().isEmpty()) {
			final List<Flux<Optional<JsonNode>>> parameterFluxes = new ArrayList<>(arguments.getArgs().size());
			for (Expression argument : arguments.getArgs()) {
				parameterFluxes.add(argument.evaluate(ctx, isBody, relativeNode));
			}
			return Flux.combineLatest(parameterFluxes, paramNodes -> {
				final ArrayNode argumentsArray = JsonNodeFactory.instance.arrayNode();
				argumentsArray.add(node.orElseThrow((() -> Exceptions.propagate(
						new PolicyEvaluationException(UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION)))));
				for (Object paramNode : paramNodes) {
					argumentsArray.add(((Optional<JsonNode>) paramNode).orElseThrow(() -> Exceptions.propagate(
							new PolicyEvaluationException(UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION))));
				}
				try {
					return ctx.getFunctionCtx().evaluate(fullyQualifiedName, argumentsArray);
				} catch (FunctionException e) {
					throw Exceptions.propagate(
							new PolicyEvaluationException(String.format(FILTER_FUNCTION_EVALUATION, function), e));
				}
			}).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
		} else {
			try {
				final ArrayNode argumentsArray = JsonNodeFactory.instance.arrayNode();
				argumentsArray.add(node.orElseThrow((() -> Exceptions.propagate(
						new PolicyEvaluationException(UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION)))));
				return Flux.just(ctx.getFunctionCtx().evaluate(fullyQualifiedName, argumentsArray));
			} catch (FunctionException e) {
				return Flux
						.error(new PolicyEvaluationException(String.format(FILTER_FUNCTION_EVALUATION, function), e));
			}
		}
	}

	@Override
	public Flux<ResultNode> applyStep(Step step, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return step.apply(this, ctx, isBody, relativeNode);
	}

	/**
	 * Applies a filter function to this JSON node.
	 *
	 * @param function     the name of the filter function
	 * @param arguments    other arguments to be passed to the function as a JSON
	 *                     array
	 * @param each         true if the selected node should be treated as an array
	 *                     and the filter function should be applied to each of its
	 *                     items
	 * @param ctx          the evaluation context
	 * @param isBody       true if the expression occurs within the policy body
	 *                     (attribute finder steps are only allowed if set to true)
	 * @param relativeNode the node a relative expression evaluates to
	 * @return a flux of {@link ResultNode.Void} instances, each indicating a
	 *         finished application of the function
	 */
	public abstract Flux<Void> applyFilterWithRelativeNode(String function, Arguments arguments, boolean each,
			EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode);

	/**
	 * The method checks whether two AbstractAnnotatedJsonNodes reference the same
	 * nodes in a structure, considering the parent nodes and access information.
	 * Cannot be applied to a JsonNodeWithoutParent.
	 *
	 * @param other the other annotated JSON node
	 * @return true if the annotated JSON node references the same node
	 * @throws PolicyEvaluationException in case the annotated JSON node is a
	 *                                   JsonNodeWithoutParent
	 */
	public abstract boolean sameReference(AbstractAnnotatedJsonNode other) throws PolicyEvaluationException;
}
