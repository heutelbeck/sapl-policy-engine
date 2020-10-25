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

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.Void;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Flux;

/**
 * Represents a JsonNode which is the item of an ArrayNode in the tree on which
 * the selection is performed.
 */
@Value
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class JsonNodeWithParentArray extends AbstractAnnotatedJsonNode {

	private int index;

	public JsonNodeWithParentArray(Val node, Val parent, int index) {
		super(node, parent);
		this.index = index;
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
		return true;
	}

	@Override
	public void removeFromTree(boolean each) throws PolicyEvaluationException {
		if (each) {
			removeEachItem(node);
		} else {
			((ArrayNode) parent.get()).remove(index);
		}
	}

	@Override
	public Flux<Void> applyFilter(String function, Arguments arguments, boolean each, EvaluationContext ctx,
			boolean isBody) {
		return applyFilterWithRelativeNode(function, arguments, each, ctx, isBody, parent);
	}

	@Override
	public Flux<Void> applyFilterWithRelativeNode(String function, Arguments arguments, boolean each,
			EvaluationContext ctx, boolean isBody, Val relativeNode) {
		if (each) {
			return applyFilterToEachItem(node, function, arguments, ctx, isBody);
		} else {
			return applyFilterToNode(node, function, arguments, ctx, isBody, relativeNode).map(filteredNode -> {
				((ArrayNode) parent.get()).set(index, filteredNode.get());
				return Void.INSTANCE;
			});

		}
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other) throws PolicyEvaluationException {
		return other.isNodeWithParentArray() && other.getParent().isDefined() && getParent().isDefined()
				&& other.getParent().get() == getParent().get()
				&& ((JsonNodeWithParentArray) other).getIndex() == getIndex();
	}

}
