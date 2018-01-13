package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;

public class BasicExpressionImplCustom extends io.sapl.grammar.sapl.impl.BasicExpressionImpl {

	private static final String SUBTEMPLATE_NO_ARRAY = "Subtemplate can only be applied to an array.";

	/**
	 * Method which is supposed to be inherited by the various subclasses of
	 * BasicExpression and used in their
	 *
	 * <pre>
	 * evaluate
	 * </pre>
	 *
	 * method.
	 *
	 * The method takes the JsonNode from evaluating the first part of the
	 * BasicExpression and applies the selection steps as well as a filter or
	 * subtemplate specified in the BasicExpression.
	 *
	 *
	 * @param resultBeforeSteps
	 *            the result before evaluating selection steps, filter or
	 *            subtemplate
	 * @param steps
	 *            the selection steps
	 * @param ctx
	 *            the evaluation context
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @param relativeNode
	 *            the node a relative expression would point to
	 * @return the JsonNode after evaluating the steps, filter and subtemplate
	 * @throws PolicyEvaluationException
	 *             in case there is an error
	 */
	protected JsonNode evaluateStepsFilterSubtemplate(JsonNode resultBeforeSteps, EList<Step> steps,
			EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode result = resolveSteps(resultBeforeSteps, steps, ctx, isBody, relativeNode).asJsonWithoutAnnotations();

		if (subtemplate != null) {
			result = evaluateSubtemplate(result, ctx, isBody);
		}

		if (filter != null) {
			result = filter.apply(result, ctx, relativeNode);
		}

		return result;
	}

	/**
	 * Method for application of a number of selection steps to a JsonNode. The
	 * method returns a result tree, i.e., either an annotated JsonNode or an array
	 * of annotated JsonNodes. The annotation contains the parent node of the
	 * JsonNode in the JSON tree of which the root is the input JsonNode. This
	 * allows for modifying or deleting the selected JsonNodes.
	 *
	 * @param rootNode
	 *            the input JsonNode
	 * @param steps
	 *            the selection steps
	 * @param ctx
	 *            the evaluation context
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @param relativeNode
	 *            the node a relative expression would point to
	 * @return the root node of the result tree (either an annotated JsonNode or an
	 *         array)
	 * @throws PolicyEvaluationException
	 *             in case there is an error
	 */
	public ResultNode resolveSteps(JsonNode rootNode, EList<Step> steps, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		ResultNode result = new JsonNodeWithoutParent(rootNode);
		if (steps != null) {
			for (Step step : steps) {
				result = result.applyStep(step, ctx, isBody, relativeNode);
			}
		}
		return result;
	}

	/**
	 * The function applies a subtemplate to an array. I.e., it evaluates an
	 * expression for each of the items and replaces each items with the result. The
	 * replaced array is returned.
	 *
	 * @param preliminaryResult
	 *            the array
	 * @param ctx
	 *            the evaluation context
	 * @param isBody
	 *            true if the expression is evaluated in the policy body
	 * @return the altered array
	 * @throws PolicyEvaluationException
	 *             in case the input JsonNode is no array or an error occurs while
	 *             evaluating the expression
	 */
	private JsonNode evaluateSubtemplate(JsonNode preliminaryResult, EvaluationContext ctx, boolean isBody)
			throws PolicyEvaluationException {
		if (!preliminaryResult.isArray()) {
			throw new PolicyEvaluationException(SUBTEMPLATE_NO_ARRAY);
		}
		for (int i = 0; i < ((ArrayNode) preliminaryResult).size(); i++) {
			((ArrayNode) preliminaryResult).set(i,
					subtemplate.evaluate(ctx, isBody, ((ArrayNode) preliminaryResult).get(i)));
		}
		return preliminaryResult;
	}
}
