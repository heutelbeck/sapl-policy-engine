package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;

public class FilterComponentImplCustom extends io.sapl.grammar.sapl.impl.FilterComponentImpl {

	protected static final String FILTER_REMOVE = "remove";
	protected static final String SUBTEMPLATE_NO_ARRAY = "Subtemplate can only be applied to an array.";

	protected static final String FILTER_REMOVE_ROOT = "Filter cannot remove the root of the tree the filter is applied to.";

	protected static final String FILTER_EACH_NO_ARRAY = "Trying to filter each element of array, but got type %s.";
	protected static final String FILTER_FUNCTION_EVALUATION = "Custom filter function '%s' could not be evaluated.";

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	/**
	 * The method takes a JSON tree, performs a number of selection steps on this
	 * tree and applies a filter function to the selected nodes. The root node of
	 * the filtered tree is returned (which is the original root in case the root
	 * node is not modified).
	 *
	 * @param rootNode
	 *            the root node of the tree to be filtered
	 * @param function
	 *            the name of the filter function
	 * @param arguments
	 *            arguments to be passed to the function, as JSON array
	 * @param steps
	 *            the steps
	 * @param each
	 *            true if the selected node should be treated as an array and the
	 *            filter function should be applied to each of its items
	 * @param ctx
	 *            the evaluation context
	 * @param relativeNode
	 *            the JSON node a relative expression would evaluate to (or null if
	 *            relative expressions are not allowed)
	 * @return the root node of the filtered tree
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during the evaluation of if the filter
	 *             function is remove
	 */
	protected JsonNode applyFilterStatement(JsonNode rootNode, String function, Arguments arguments, EList<Step> steps,
			boolean each, EvaluationContext ctx, JsonNode relativeNode) throws PolicyEvaluationException {
		ResultNode target = new JsonNodeWithoutParent(rootNode);
		if (steps != null) {
			for (Step step : steps) {
				target = target.applyStep(step, ctx, true, relativeNode);
			}
		}

		if (target.isNodeWithoutParent() && !each) {
			return getFilteredRoot(target, function, arguments, each, ctx);
		} else {
			applyFilter(target, function, arguments, each, ctx);
			return rootNode;
		}
	}

	/**
	 * The function is used to apply a filter function to a node and receive the
	 * result. The function is supposed to be used if filtering should be applied to
	 * the root of a JSON tree.
	 *
	 * @param target
	 *            the selected node to be filtered
	 * @param function
	 *            the name of the filter function
	 * @param arguments
	 *            arguments to be passed to the function
	 * @param each
	 *            true if the selected node should be treated as an array and the
	 *            filter function should be applied to each of its items
	 * @param ctx
	 *            the evaluation context
	 * @return the result which is returned by the filter function
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during the evaluation of if the filter
	 *             function is remove
	 */
	protected static JsonNode getFilteredRoot(ResultNode target, String function, Arguments arguments, boolean each,
			EvaluationContext ctx) throws PolicyEvaluationException {
		if (FILTER_REMOVE.equals(function)) {
			throw new PolicyEvaluationException(FILTER_REMOVE_ROOT);
		}

		return AbstractAnnotatedJsonNode.applyFunctionToNode(function, target.asJsonWithoutAnnotations(), arguments,
				ctx, null);
	}

	/**
	 * Applies a filter function to a selected JSON node. The selected node is
	 * changed in the tree, no value is returned. Thus, the caller must ensure that
	 * the root node will be left unchanged.
	 *
	 * @param target
	 *            the selected node to be filtered
	 * @param function
	 *            the name of the filter function
	 * @param arguments
	 *            arguments to be passed to the function
	 * @param each
	 *            true if the selected node should be treated as an array and the
	 *            filter function should be applied to each of its items
	 * @param ctx
	 *            the evaluation context
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during evaluation
	 */
	protected static void applyFilter(ResultNode target, String function, Arguments arguments, boolean each,
			EvaluationContext ctx) throws PolicyEvaluationException {
		if (FILTER_REMOVE.equals(function)) {
			target.removeFromTree(each);
		} else {
			target.applyFunction(function, arguments, each, ctx);
		}
	}

}
