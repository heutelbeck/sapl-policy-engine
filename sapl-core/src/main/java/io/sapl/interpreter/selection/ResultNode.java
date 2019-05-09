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

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Interface representing a node in a selection result tree.
 *
 * The node can be a JsonNode which is annotated with its parent node in a JsonTree (which
 * is an ObjectNode or an ArrayNode) and a unique access method (i.e., attribute name or
 * array index). The node can also be a result array containing zero to many JsonNodes
 * with annotations.
 *
 * The annotations allow both for modifying the selected node in a Jackson JSON tree or
 * checking whether selected nodes are not only equal, but also identical inside a Jackson
 * JSON tree.
 */
public interface ResultNode {

	class Void {

		public static final Void INSTANCE = new Void();

	}

	/**
	 * Removes all annotations from the selection result tree and returns the root
	 * JsonNode.
	 * @return the root JsonNode
	 */
	Optional<JsonNode> asJsonWithoutAnnotations();

	/**
	 * Checks if the node is a result array (which can contain multiple AnnotatedJsonNodes
	 * as children).
	 * @return true, if the node is a result array
	 */
	boolean isResultArray();

	/**
	 * Checks if the node is an annotated JsonNode with no parents.
	 * @return true, if the node is an annotated JsonNode with no parents
	 */
	boolean isNodeWithoutParent();

	/**
	 * Checks if the node is an annotated JsonNode with an object as parent node.
	 * @return true, if the node is an annotated JsonNode with an object as parent node
	 */
	boolean isNodeWithParentObject();

	/**
	 * Checks if the node is an annotated JsonNode with an array as parent node.
	 * @return true, if the node is an annotated JsonNode with an array as parent node
	 */
	boolean isNodeWithParentArray();

	/**
	 * Removes the selected JsonNode from its parent. If the selected node is an array,
	 * the param each can be used to specify that each element should be removed from this
	 * array.
	 * @param each true, if the selection should be treated as an array and the remove
	 * operation should be applied to each item
	 * @throws PolicyEvaluationException in case the remove operation could not be applied
	 */
	void removeFromTree(boolean each) throws PolicyEvaluationException;

	/**
	 * Applies a filter function to the selected JsonNode. If the selected node is an
	 * array, the param each can be used to specify that the filter function should be
	 * applied to each item of this array.
	 * @param function name of the filter function
	 * @param arguments arguments to pass to the filter function
	 * @param each true, if the selection should be treated as an array and the filter
	 * function should be applied to each of its items
	 * @param ctx the evaluation context
	 * @param isBody true if the filter is applied within the policy body
	 * @return a flux of {@link ResultNode.Void} instances, each indicating a finished
	 * application of the filter function to the selected JsonNode or its child elements.
	 */
	Flux<Void> applyFilter(String function, Arguments arguments, boolean each,
			EvaluationContext ctx, boolean isBody);

	/**
	 * Applies a step to the result node and returns a {@link Flux} of new result nodes.
	 * @param step the step to apply
	 * @param ctx the evaluation context
	 * @param isBody true if the step is applied within the policy body
	 * @param relativeNode the node a relative expression evaluates to
	 * @return a {@link Flux} of result nodes resulting from application of the step
	 */
	Flux<ResultNode> applyStep(Step step, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode);

}
