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

/**
 * Represents an annotated JsonNode in a selection result tree.
 */
@Data
@AllArgsConstructor
public abstract class AbstractAnnotatedJsonNode implements ResultNode {

	private static final String FILTER_FUNCTION_EVALUATION = "Custom filter function '%s' could not be evaluated.";
	private static final String FILTER_EACH_NO_ARRAY = "Trying to filter each element of array, but got type %s.";

	protected JsonNode node;
	protected JsonNode parent;

	@Override
	public JsonNode asJsonWithoutAnnotations() {
		return node;
	}

	@Override
	public boolean isResultArray() {
		return false;
	}

	/**
	 * Removes each item from a JsonNode.
	 *
	 * @param parentNode
	 *            the parent JsonNode node
	 * @throws PolicyEvaluationException
	 *             in case the parentNode is no array
	 */
	protected static void removeEachItem(JsonNode parentNode) throws PolicyEvaluationException {
		if (!parentNode.isArray()) {
			throw new PolicyEvaluationException(String.format(FILTER_EACH_NO_ARRAY, parentNode.getNodeType()));
		} else {
			((ArrayNode) parentNode).removeAll();
		}
	}

	/**
	 * Replaces each item of an array by the result of evaluating a filter function
	 * for the item.
	 *
	 * @param function
	 *            name of the filter function
	 * @param parentNode
	 *            the parent JsonNode
	 * @param arguments
	 *            arguments to be passed to the function as a JSON array
	 * @param ctx
	 *            the evaluation context
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during evaluation, e.g., if the
	 *             parentNode is not an array or if the function cannot be evaluated
	 */
	protected static void applyFunctionToEachItem(String function, JsonNode parentNode, Arguments arguments,
			EvaluationContext ctx) throws PolicyEvaluationException {
		if (!parentNode.isArray()) {
			throw new PolicyEvaluationException(String.format(FILTER_EACH_NO_ARRAY, parentNode.getNodeType()));
		}

		for (int i = 0; i < parentNode.size(); i++) {
			((ArrayNode) parentNode).set(i,
					applyFunctionToNode(function, parentNode.get(i), arguments, ctx, parentNode.get(i)));
		}
	}

	/**
	 * Applies a function to a JSON node and returns the result.
	 *
	 * @param function
	 *            the name of the function
	 * @param node
	 *            the JSON node to apply the function to
	 * @param arguments
	 *            other arguments to be passed to the function as a JSON array
	 * @param ctx
	 *            the evaluation context
	 * @return the result as a JsonNode
	 * @throws PolicyEvaluationException
	 *             in case there is an error during the evaluation
	 */
	public static JsonNode applyFunctionToNode(String function, JsonNode node, Arguments arguments,
			EvaluationContext ctx, JsonNode relativeNode) throws PolicyEvaluationException {
		ArrayNode args = JsonNodeFactory.instance.arrayNode();
		args.add(node);
		if (arguments != null) {
			for (Expression argument : arguments.getArgs()) {
				args.add(argument.evaluate(ctx, true, relativeNode));
			}
		}

		String fullyQualifiedName = function;
		if (ctx.getImports().containsKey(function)) {
			fullyQualifiedName = ctx.getImports().get(function);
		}

		try {
			return ctx.getFunctionCtx().evaluate(fullyQualifiedName, args);
		} catch (FunctionException e) {
			throw new PolicyEvaluationException(String.format(FILTER_FUNCTION_EVALUATION, function), e);
		}
	}

	@Override
	public ResultNode applyStep(Step step, EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
			throws PolicyEvaluationException {
		return step.apply(this, ctx, isBody, relativeNode);
	}

	/**
	 * The method checks whether two AbstractAnnotatedJsonNodes reference the same
	 * nodes in a structure, considering the parent nodes and access information.
	 * Cannot be applied to a JsonNodeWithoutParent.
	 *
	 * @param other
	 *            the other annotated JSON node
	 * @return true if the annotated JSON node references the same node
	 * @throws PolicyEvaluationException
	 *             in case the annotated JSON node is a JsonNodeWithoutParent
	 */
	public abstract boolean sameReference(AbstractAnnotatedJsonNode other) throws PolicyEvaluationException;
}
