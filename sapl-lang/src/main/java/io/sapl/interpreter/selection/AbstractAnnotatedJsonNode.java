/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.Void;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
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

	protected Val node;

	protected Val parent;

	/**
	 * Create from an optional node
	 * 
	 * @param node base node
	 */
	public AbstractAnnotatedJsonNode(Val node) {
		this.node = node;
		this.parent = Val.undefined();
	}

	@Override
	public Val asJsonWithoutAnnotations() {
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
	protected static void removeEachItem(Val parentNode) throws PolicyEvaluationException {
		if (parentNode.isUndefined() || !parentNode.get().isArray()) {
			throw new PolicyEvaluationException(FILTER_EACH_NO_ARRAY,
					parentNode.isDefined() ? parentNode.get().getNodeType() : "undefined");
		} else {
			((ArrayNode) parentNode.get()).removeAll();
		}
	}

	/**
	 * Replaces each item of an array by the result of evaluating a filter function
	 * for the item.
	 * 
	 * @param parentNode the parent JsonNode (must be an array node)
	 * @param function   name of the filter function
	 * @param arguments  arguments to be passed to the function as a JSON array
	 * @param ctx        the evaluation context
	 * @return a flux of {@link Void} instances, each indicating a finished
	 *         application of the function to each item of the parent array node
	 */
	protected static Flux<Void> applyFilterToEachItem(Val parentNode, String function, Arguments arguments,
			EvaluationContext ctx) {
		if (parentNode.isUndefined() || !parentNode.get().isArray()) {
			return Flux.error(new PolicyEvaluationException(FILTER_EACH_NO_ARRAY,
					parentNode.isUndefined() ? parentNode.get().getNodeType() : "undefined"));
		}

		final ArrayNode arrayNode = (ArrayNode) parentNode.get();

		final String fullyQualifiedName = ctx.getImports().getOrDefault(function, function);
		if (arguments != null && !arguments.getArgs().isEmpty()) {
			final List<Flux<Val>> parameterFluxes = new ArrayList<>(arguments.getArgs().size());
			for (Expression argument : arguments.getArgs()) {
				parameterFluxes.add(argument.evaluate(ctx, parentNode));
			}
			return Flux.combineLatest(parameterFluxes, paramNodes -> {
				for (int i = 0; i < arrayNode.size(); i++) {
					final JsonNode childNode = arrayNode.get(i);
					final Val[] argumentsArray = new Val[paramNodes.length + 1];
					argumentsArray[0] = Val.of(childNode);
					int j = 1;
					for (Object paramNode : paramNodes) {
						argumentsArray[j++] = (Val) paramNode;
					}
					try {
						final Val modifiedChildNode = ctx.getFunctionCtx().evaluate(fullyQualifiedName, argumentsArray);
						if (modifiedChildNode.isDefined()) {
							arrayNode.set(i, modifiedChildNode.get());
						} else {
							return Flux.<Void>error(new PolicyEvaluationException(
									UNDEFINED_VALUES_CANNOT_BE_ADDED_TO_RESULTS_IN_JSON_FORMAT));
						}
					} catch (FunctionException e) {
						return Flux.<Void>error(new PolicyEvaluationException(e, FILTER_FUNCTION_EVALUATION, function));
					}
				}
				return Flux.just(Void.INSTANCE);
			}).flatMap(Function.identity());
		} else {
			try {
				for (int i = 0; i < arrayNode.size(); i++) {
					final Val modifiedChildNode = ctx.getFunctionCtx().evaluate(fullyQualifiedName,
							new Val[] { Val.of(arrayNode.get(i)) });
					if (modifiedChildNode.isDefined()) {
						arrayNode.set(i, modifiedChildNode.get());
					} else {
						return Flux.error(new PolicyEvaluationException(
								UNDEFINED_VALUES_CANNOT_BE_ADDED_TO_RESULTS_IN_JSON_FORMAT));
					}
				}
				return Flux.just(Void.INSTANCE);
			} catch (FunctionException e) {
				return Flux.error(new PolicyEvaluationException(e, FILTER_FUNCTION_EVALUATION, function));
			}
		}
	}

	/**
	 * Applies a function to a JSON node and returns a flux of the results.
	 * 
	 * @param optNode      the JSON node to apply the function to
	 * @param function     the name of the function
	 * @param arguments    other arguments to be passed to the function as a JSON
	 *                     array
	 * @param ctx          the evaluation context
	 * @param relativeNode the node a relative expression evaluates to
	 * @return a flux of the results as JsonNodes
	 */
	public static Flux<Val> applyFilterToNode(Val optNode, String function, Arguments arguments, EvaluationContext ctx,
			Val relativeNode) {
		if (optNode.isUndefined()) {
			return Flux.error(new PolicyEvaluationException(UNDEFINED_VALUES_HANDED_OVER_TO_FUNCTION_EVALUATION));
		}

		final String fullyQualifiedName = ctx.getImports().getOrDefault(function, function);

		if (arguments != null && !arguments.getArgs().isEmpty()) {
			final List<Flux<Val>> parameterFluxes = new ArrayList<>(arguments.getArgs().size());
			for (Expression argument : arguments.getArgs()) {
				parameterFluxes.add(argument.evaluate(ctx, relativeNode));
			}
			return Flux.combineLatest(parameterFluxes, paramNodes -> {
				final Val[] parmeters = new Val[paramNodes.length + 1];
				parmeters[0] = optNode;
				int j = 1;
				for (Object param : paramNodes) {
					parmeters[j++] = (Val) param;
				}
				try {
					return Flux.just(ctx.getFunctionCtx().evaluate(fullyQualifiedName, parmeters));
				} catch (FunctionException e) {
					return Flux.<Val>error(new PolicyEvaluationException(e, FILTER_FUNCTION_EVALUATION, function));
				}
			}).flatMap(Function.identity());
		} else {
			try {
				return Flux.just(ctx.getFunctionCtx().evaluate(fullyQualifiedName, new Val[] { optNode }));
			} catch (FunctionException e) {
				return Flux.error(new PolicyEvaluationException(e, FILTER_FUNCTION_EVALUATION, function));
			}
		}
	}

	@Override
	public Flux<ResultNode> applyStep(Step step, EvaluationContext ctx, @NonNull Val relativeNode) {
		return step.apply(this, ctx, relativeNode);
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
	 * @param relativeNode the node a relative expression evaluates to
	 * @return a flux of {@link Void} instances, each indicating a finished
	 *         application of the function
	 */
	public abstract Flux<Void> applyFilterWithRelativeNode(String function, Arguments arguments, boolean each,
			EvaluationContext ctx, @NonNull Val relativeNode);

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
