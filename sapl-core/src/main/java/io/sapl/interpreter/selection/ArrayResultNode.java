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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import lombok.EqualsAndHashCode;
import lombok.Value;
import reactor.core.publisher.Flux;

/**
 * Represents an array node in a selection result tree. Its items have to be
 * AnnotatedJsonNodes.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ArrayResultNode implements ResultNode, Iterable<AbstractAnnotatedJsonNode> {
	protected static final String FILTER_HELPER_ARRAY = "Cannot apply filter to helper array.";

	List<AbstractAnnotatedJsonNode> nodes;

	@Override
	public JsonNode asJsonWithoutAnnotations() {
		ArrayNode returnNode = JsonNodeFactory.instance.arrayNode();
		for (AbstractAnnotatedJsonNode node : nodes) {
			returnNode.add(node.getNode());
		}
		return returnNode;
	}

	@Override
	public Iterator<AbstractAnnotatedJsonNode> iterator() {
		return nodes.iterator();
	}

	@Override
	public boolean isResultArray() {
		return true;
	}

	@Override
	public boolean isNodeWithoutParent() {
		return false;
	}

	@Override
	public boolean isNodeWithParentObject() {
		return false;
	}

	@Override
	public boolean isNodeWithParentArray() {
		return false;
	}

	@Override
	public void removeFromTree(boolean each) throws PolicyEvaluationException {
		if (each) {
			for (AbstractAnnotatedJsonNode node : changeOrderForRemove(nodes)) {
				node.removeFromTree(false);
			}
		} else {
			throw new PolicyEvaluationException(FILTER_HELPER_ARRAY);
		}
	}

	@Override
	public void applyFunction(String function, Arguments arguments, boolean each, EvaluationContext ctx)
			throws PolicyEvaluationException {
		if (each) {
			for (AbstractAnnotatedJsonNode node : nodes) {
				node.applyFunctionWithRelativeNode(function, arguments, false, ctx, node.getNode());
			}
		} else {
			throw new PolicyEvaluationException(FILTER_HELPER_ARRAY);
		}
	}

	/**
	 * The helper method prepares a list of AnnotatedJsonNodes for applying the
	 * remove filter function. It changes the order of the contained annotated
	 * JsonNodes so that nodes with an array as parent are in descending order based
	 * on the index. This ensures that removal of a node does not affect the index
	 * of other nodes that are to be removed from the same array.
	 *
	 * @param nodes
	 *            the annotated nodes to prepare
	 * @return a result array with the correct ordering of its children
	 */
	private static List<AbstractAnnotatedJsonNode> changeOrderForRemove(List<AbstractAnnotatedJsonNode> nodes) {
		List<AbstractAnnotatedJsonNode> result = new ArrayList<>();
		Map<Integer, List<AbstractAnnotatedJsonNode>> nodesWithParentArray = new HashMap<>();

		for (AbstractAnnotatedJsonNode node : nodes) {
			if (node.isNodeWithParentArray()) {
				int index = ((JsonNodeWithParentArray) node).getIndex();
				if (!nodesWithParentArray.containsKey(index)) {
					nodesWithParentArray.put(index, new ArrayList<>());
				}
				nodesWithParentArray.get(index).add(node);
			} else {
				result.add(node);
			}
		}

		List<Integer> indices = new ArrayList<>(nodesWithParentArray.keySet());
		Collections.sort(indices);
		Collections.reverse(indices);

		for (Integer index : indices) {
			result.addAll(nodesWithParentArray.get(index));
		}

		return result;
	}

	@Override
	public ResultNode applyStep(Step step, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		return step.apply(this, ctx, isBody, relativeNode);
	}

	@Override
	public Flux<ResultNode> reactiveApplyStep(Step step, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		return step.reactiveApply(this, ctx, isBody, relativeNode);
	}
}
