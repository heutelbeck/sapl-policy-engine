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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ResultNode;
import org.eclipse.emf.common.util.EList;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

public class FilterComponentImplCustom extends io.sapl.grammar.sapl.impl.FilterComponentImpl {

	protected static final String FILTER_REMOVE = "remove";
	protected static final String FILTER_REMOVE_ROOT = "Filter cannot remove the root of the tree the filter is applied to.";

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
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @param relativeNode
	 *            the JSON node a relative expression would evaluate to (or null if
	 *            relative expressions are not allowed)
	 * @return the root node of the filtered tree
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during the evaluation if the filter
	 *             function is remove
	 */
	protected JsonNode applyFilterStatement(JsonNode rootNode, String function, Arguments arguments, EList<Step> steps,
			boolean each, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {

        final ResultNode target = StepResolver.resolveSteps(rootNode, steps, ctx, isBody, relativeNode);
        if (target.isNodeWithoutParent() && !each) {
			return getFilteredRoot(target, function, arguments, ctx, isBody);
		} else {
			applyFilter(target, function, arguments, each, ctx, isBody);
			return rootNode;
		}
	}

    /**
     * The method takes a JSON tree, performs a number of selection steps on this
     * tree and applies a filter function to the selected nodes. A flux of root nodes
     * of the filtered tree is returned (which are the original roots in case the root
     * nodes are not modified).
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
     * @param isBody
     *            true if the expression occurs within the policy body (attribute
     *            finder steps are only allowed if set to true)
     * @param relativeNode
     *            the JSON node a relative expression would evaluate to (or null if
     *            relative expressions are not allowed)
     * @return a Flux of root nodes of the filtered tree
     */
    protected Flux<JsonNode> reactiveApplyFilterStatement(JsonNode rootNode, String function, Arguments arguments, EList<Step> steps,
														  boolean each, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
        return StepResolver.reactiveResolveSteps(rootNode, steps, ctx, isBody, relativeNode)
                .switchMap(resultNode -> {
                    if (resultNode.isNodeWithoutParent() && !each) {
                        return reactiveGetFilteredRoot(resultNode, function, arguments, ctx, isBody);
                    } else {
                        return reactiveApplyFilter(resultNode, function, arguments, each, ctx, isBody)
                                .map(voidType -> rootNode);
                    }
                });
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
	 * @param ctx
	 *            the evaluation context
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @return the result which is returned by the filter function
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during the evaluation of if the filter
	 *             function is remove
	 */
	protected static JsonNode getFilteredRoot(ResultNode target, String function, Arguments arguments,
											  EvaluationContext ctx, boolean isBody) throws PolicyEvaluationException {
		if (FILTER_REMOVE.equals(function)) {
			throw new PolicyEvaluationException(FILTER_REMOVE_ROOT);
		}
		return AbstractAnnotatedJsonNode.applyFilterToNode(function, target.asJsonWithoutAnnotations(), arguments, ctx, isBody,null);
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
	 * @param ctx
	 *            the evaluation context
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @return the stream of results returned by the reactive filter function
	 */
	protected static Flux<JsonNode> reactiveGetFilteredRoot(ResultNode target, String function, Arguments arguments,
															EvaluationContext ctx, boolean isBody) {
		if (FILTER_REMOVE.equals(function)) {
			return Flux.error(new PolicyEvaluationException(FILTER_REMOVE_ROOT));
		}
		return AbstractAnnotatedJsonNode.reactiveApplyFilterToNode(function, target.asJsonWithoutAnnotations(), arguments, ctx, isBody, null);
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
	 * @param isBody
	 *            true if the expression occurs within the policy body (attribute
	 *            finder steps are only allowed if set to true)
	 * @throws PolicyEvaluationException
	 *             in case an error occurs during evaluation
	 */
	protected static void applyFilter(ResultNode target, String function, Arguments arguments, boolean each,
									  EvaluationContext ctx, boolean isBody) throws PolicyEvaluationException {
		if (FILTER_REMOVE.equals(function)) {
			target.removeFromTree(each);
		} else {
			target.applyFilter(function, arguments, each, ctx, isBody);
		}
	}

    /**
     * Applies a filter function to a selected JSON node. The selected node is
     * changed in the tree. The caller must ensure that the root node will be
     * left unchanged.
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
     * @param isBody
     *            true if the expression occurs within the policy body (attribute
     *            finder steps are only allowed if set to true)
     * @return a flux of {@link ResultNode.Void} instances, each indicating a finished
     *         application of the filter function
	 */
	protected static Flux<ResultNode.Void> reactiveApplyFilter(ResultNode target, String function, Arguments arguments, boolean each,
                                                               EvaluationContext ctx, boolean isBody) {
        if (FILTER_REMOVE.equals(function)) {
            return Flux.defer(() -> {
                        try {
                            target.removeFromTree(each);
                            return Flux.just(ResultNode.Void.INSTANCE);
                        } catch (PolicyEvaluationException e) {
                            throw Exceptions.propagate(e);
                        }
                    });
        } else {
            return target.reactiveApplyFilter(function, arguments, each, ctx, isBody);
        }
	}

}
