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

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Represents a JsonNode which has no parent node (array or object) in the tree on which the selection is performed. Typically the root element of the tree.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JsonNodeWithoutParent extends AbstractAnnotatedJsonNode {

	private static final String REFERENCE_CANNOT_BE_COMPARED = "Reference of a JsonNodeWithoutParent cannot be compared.";
	private static final String FILTER_ROOT_ELEMENT = "The root element cannot be filtered.";

	public JsonNodeWithoutParent(JsonNode node) {
		super(node, null);
	}

	@Override
	public boolean isNodeWithoutParent() {
		return true;
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
			removeEachItem(node);
		} else {
			throw new PolicyEvaluationException(FILTER_ROOT_ELEMENT);
		}
	}

	@Override
	public void applyFunction(String function, Arguments arguments, boolean each, EvaluationContext ctx)
			throws PolicyEvaluationException {
		applyFunctionWithRelativeNode(function, arguments, each, ctx, null);
	}
	
	@Override
	void applyFunctionWithRelativeNode(String function, Arguments arguments, boolean each, EvaluationContext ctx,
			JsonNode relativeNode) throws PolicyEvaluationException {
		if (each) {
			applyFunctionToEachItem(function, node, arguments, ctx);
		} else {
			throw new PolicyEvaluationException(FILTER_ROOT_ELEMENT);
		}
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other)  throws PolicyEvaluationException {
		throw new PolicyEvaluationException(REFERENCE_CANNOT_BE_COMPARED);
	}
}
