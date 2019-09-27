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
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.Void;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Flux;

/**
 * Represents a JsonNode which is the value of an attribute of an ObjectNode in the tree
 * on which the selection is performed.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JsonNodeWithParentObject extends AbstractAnnotatedJsonNode {

	private String attribute;

	public JsonNodeWithParentObject(Optional<JsonNode> node, Optional<JsonNode> parent, String attribute) {
		super(node, parent);
		this.attribute = attribute;
	}

	@Override
	public boolean isNodeWithoutParent() {
		return false;
	}

	@Override
	public boolean isNodeWithParentObject() {
		return true;
	}

	@Override
	public boolean isNodeWithParentArray() {
		return false;
	}

	@Override
	public void removeFromTree(boolean each) throws PolicyEvaluationException {
		if (each) {
			removeEachItem(node);
		}
		else {
			((ObjectNode) parent.get()).remove(attribute);
		}
	}

	@Override
	public Flux<Void> applyFilter(String function, Arguments arguments, boolean each, EvaluationContext ctx,
			boolean isBody) {
		return applyFilterWithRelativeNode(function, arguments, each, ctx, isBody, parent);
	}

	@Override
	public Flux<Void> applyFilterWithRelativeNode(String function, Arguments arguments, boolean each,
			EvaluationContext ctx, boolean isBody, Optional<JsonNode> relativeNode) {
		if (each) {
			return applyFilterToEachItem(node, function, arguments, ctx, isBody);
		}
		else {
			return applyFilterToNode(node, function, arguments, ctx, isBody, relativeNode).map(filteredNode -> {
				((ObjectNode) parent.get()).set(attribute, filteredNode.get());
				return Void.INSTANCE;
			});
		}
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other) throws PolicyEvaluationException {
		return other.isNodeWithParentObject() && other.getParent().isPresent() && getParent().isPresent()
				&& other.getParent().get() == getParent().get()
				&& getAttribute().equals(((JsonNodeWithParentObject) other).getAttribute());
	}

}
