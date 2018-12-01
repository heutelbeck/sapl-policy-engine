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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import org.eclipse.emf.common.util.EList;
import reactor.core.publisher.Flux;

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
	protected JsonNode evaluateStepsFilterSubtemplate(JsonNode resultBeforeSteps, EList<Step> steps, EvaluationContext ctx,
													  boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode result = resolveSteps(resultBeforeSteps, steps, ctx, isBody, relativeNode).asJsonWithoutAnnotations();

		if (filter != null) {
			result = filter.apply(result, ctx, relativeNode);
		} else if (subtemplate != null) {
            result = evaluateSubtemplate(result, ctx, isBody);
        }

		return result;
	}

	protected Flux<JsonNode> reactiveEvaluateStepsFilterSubtemplate(JsonNode resultBeforeSteps, EList<Step> steps,
																	EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
        Flux<ResultNode> result = reactiveResolveSteps(resultBeforeSteps, steps, ctx, isBody, relativeNode);
        if (filter != null) {
            result = result.switchMap(resultNode -> {
                final JsonNode jsonNode = resultNode.asJsonWithoutAnnotations();
                return filter.reactiveApply(jsonNode, ctx, relativeNode)
                        .map(JsonNodeWithoutParent::new);
            });
        } else if (subtemplate != null) {
            result = result.switchMap(resultNode -> {
                final JsonNode jsonNode = resultNode.asJsonWithoutAnnotations();
                return reactiveEvaluateSubtemplate(jsonNode, ctx, isBody)
                        .map(JsonNodeWithoutParent::new);
            });
        }
        return result.map(ResultNode::asJsonWithoutAnnotations);
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
	public ResultNode resolveSteps(JsonNode rootNode, EList<Step> steps, EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
            throws PolicyEvaluationException {

		ResultNode result = new JsonNodeWithoutParent(rootNode);
		if (steps != null) {
			for (Step step : steps) {
				result = result.applyStep(step, ctx, isBody, relativeNode);
			}
		}
		return result;
	}

	public Flux<ResultNode> reactiveResolveSteps(JsonNode rootNode, EList<Step> steps, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
        // this implementation must be able to handle expressions like "input".<first.attr>.<second.attr>.<third.attr>... correctly
        final ResultNode result = new JsonNodeWithoutParent(rootNode);
        if (steps != null && ! steps.isEmpty()) {
            final List<FluxProvider<ResultNode>> fluxProviders = new ArrayList<>(steps.size());
            for (Step step : steps) {
                fluxProviders.add(resultNode -> resultNode.reactiveApplyStep(step, ctx, isBody, relativeNode));
            }
            return cascadingSwitchMap(result, fluxProviders, 0);
        } else {
            return Flux.just(result);
        }
	}

    private Flux<ResultNode> cascadingSwitchMap(ResultNode input, List<FluxProvider<ResultNode>> fluxProviders, int idx) {
        if (idx < fluxProviders.size()) {
            return fluxProviders.get(idx).fluxFor(input).switchMap(result -> cascadingSwitchMap(result, fluxProviders, idx + 1));
        }
        return Flux.just(input);
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
	private JsonNode evaluateSubtemplate(JsonNode preliminaryResult, EvaluationContext ctx, boolean isBody) throws PolicyEvaluationException {
		if (!preliminaryResult.isArray()) {
			throw new PolicyEvaluationException(SUBTEMPLATE_NO_ARRAY);
		}

        final ArrayNode arrayNode = (ArrayNode) preliminaryResult;
        for (int i = 0; i < arrayNode.size(); i++) {
            final JsonNode childNode = arrayNode.get(i);
            final JsonNode evaluatedSubtemplate = subtemplate.evaluate(ctx, isBody, childNode);
            arrayNode.set(i, evaluatedSubtemplate);
		}
		return arrayNode;
	}

	private Flux<JsonNode> reactiveEvaluateSubtemplate(JsonNode preliminaryResult, EvaluationContext ctx, boolean isBody) {
        if (!preliminaryResult.isArray()) {
            return Flux.error(new PolicyEvaluationException(SUBTEMPLATE_NO_ARRAY));
        }

        final ArrayNode arrayNode = (ArrayNode) preliminaryResult;
        final List<Flux<JsonNode>> fluxes = new ArrayList<>(arrayNode.size());
        for (int i = 0; i < arrayNode.size(); i++) {
            final JsonNode childNode = arrayNode.get(i);
            fluxes.add(subtemplate.reactiveEvaluate(ctx, isBody, childNode));
        }
        return Flux.combineLatest(fluxes, replacements -> {
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, (JsonNode) replacements[i]);
            }
            return arrayNode;
        });
	}

	@FunctionalInterface
	private interface FluxProvider<T>  {
	    Flux<T> fluxFor(T input);
    }
}
