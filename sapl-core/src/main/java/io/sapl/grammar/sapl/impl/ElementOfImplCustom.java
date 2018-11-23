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

import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;

public class ElementOfImplCustom extends io.sapl.grammar.sapl.impl.ElementOfImpl {

	private static final String ELEMENTOF_TYPE_MISMATCH = "Type mismatch. 'in' expects an array value on the right side, but got: '%s'.";

	private static final int HASH_PRIME_01 = 17;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
			throws PolicyEvaluationException {

		JsonNode right = getRight().evaluate(ctx, isBody, relativeNode);
		if (!right.isArray()) {
			throw new PolicyEvaluationException(String.format(ELEMENTOF_TYPE_MISMATCH, right.getNodeType()));
		}
		JsonNode left = getLeft().evaluate(ctx, isBody, relativeNode);
		for (JsonNode node : right) {
			if (left.isNumber() && node.isNumber()) {
				if (left.decimalValue().equals(node.decimalValue())) {
					return JSON.booleanNode(true);
				}
			} else if (left.equals(node)) {
				return JSON.booleanNode(true);
			}
		}
		return JSON.booleanNode(false);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_01 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_01 * hash + ((getLeft() == null) ? 0 : getLeft().hash(imports));
		hash = HASH_PRIME_01 * hash + ((getRight() == null) ? 0 : getRight().hash(imports));
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final ElementOfImplCustom otherImpl = (ElementOfImplCustom) other;
		if ((getLeft() == null) ? (getLeft() != otherImpl.getLeft())
				: !getLeft().isEqualTo(otherImpl.getLeft(), otherImports, imports)) {
			return false;
		}
		return (getRight() == null) ? (getRight() == otherImpl.getRight())
				: getRight().isEqualTo(otherImpl.getRight(), otherImports, imports);
	}

}
