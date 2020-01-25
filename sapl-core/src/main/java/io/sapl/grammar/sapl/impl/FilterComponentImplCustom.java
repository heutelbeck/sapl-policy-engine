/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.Optional;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.Void;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

public class FilterComponentImplCustom extends FilterComponentImpl {

	private static final String FILTER_REMOVE = "remove";

	private static final String FILTER_REMOVE_ROOT = "Filter cannot remove the root of the tree the filter is applied to.";

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	/**
	 * The method takes a JSON tree, performs a number of selection steps on this tree and
	 * applies a filter function to the selected nodes. A flux of root nodes of the
	 * filtered tree is returned (which are the original roots in case the root nodes are
	 * not modified).
	 * @param rootNode the root node of the tree to be filtered
	 * @param steps the selection steps to be applied to the root node
	 * @param each true if the selected node should be treated as an array and the filter
	 * function should be applied to each of its items
	 * @param function the name of the filter function
	 * @param arguments arguments to be passed to the function, as JSON array
	 * @param ctx the evaluation context
	 * @param isBody true if the expression occurs within the policy body (attribute
	 * finder steps are only allowed if set to true)
	 * @param relativeNode the JSON node a relative expression would evaluate to (or null
	 * if relative expressions are not allowed)
	 * @return a Flux of root nodes of the filtered tree
	 */
	protected Flux<Optional<JsonNode>> applyFilterStatement(Optional<JsonNode> rootNode, EList<Step> steps,
			boolean each, String function, Arguments arguments, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		return StepResolver.resolveSteps(rootNode, steps, ctx, isBody, relativeNode).switchMap(resultNode -> {
			if (resultNode.isNodeWithoutParent() && !each) {
				return getFilteredRoot(resultNode, function, arguments, ctx, isBody);
			}
			else {
				return applyFilter(resultNode, function, arguments, each, ctx, isBody).map(voidType -> rootNode);
			}
		});
	}

	/**
	 * The function is used to apply a filter function to a node and receive the result.
	 * The function is supposed to be used if filtering should be applied to the root of a
	 * JSON tree.
	 * @param target the selected node to be filtered
	 * @param function the name of the filter function
	 * @param arguments arguments to be passed to the function
	 * @param ctx the evaluation context
	 * @param isBody true if the expression occurs within the policy body (attribute
	 * finder steps are only allowed if set to true)
	 * @return the stream of results returned by the reactive filter function
	 */
	private static Flux<Optional<JsonNode>> getFilteredRoot(ResultNode target, String function, Arguments arguments,
			EvaluationContext ctx, boolean isBody) {
		if (FILTER_REMOVE.equals(function)) {
			return Flux.error(new PolicyEvaluationException(FILTER_REMOVE_ROOT));
		}
		return AbstractAnnotatedJsonNode.applyFilterToNode(target.asJsonWithoutAnnotations(), function, arguments, ctx,
				isBody, null);
	}

	/**
	 * Applies a filter function to a selected JSON node. The selected node is changed in
	 * the tree. The caller must ensure that the root node will be left unchanged.
	 * @param target the selected node to be filtered
	 * @param function the name of the filter function
	 * @param arguments arguments to be passed to the function
	 * @param each true if the selected node should be treated as an array and the filter
	 * function should be applied to each of its items
	 * @param ctx the evaluation context
	 * @param isBody true if the expression occurs within the policy body (attribute
	 * finder steps are only allowed if set to true)
	 * @return a flux of {@link Void} instances, each indicating a finished application of
	 * the filter function
	 */
	private static Flux<Void> applyFilter(ResultNode target, String function, Arguments arguments, boolean each,
			EvaluationContext ctx, boolean isBody) {
		if (FILTER_REMOVE.equals(function)) {
			return Flux.defer(() -> {
				try {
					target.removeFromTree(each);
					return Flux.just(Void.INSTANCE);
				}
				catch (PolicyEvaluationException e) {
					return Flux.error(e);
				}
			});
		}
		else {
			return target.applyFilter(function, arguments, each, ctx, isBody);
		}
	}

}
