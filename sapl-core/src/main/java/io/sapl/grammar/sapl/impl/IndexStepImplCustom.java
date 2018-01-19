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
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;

public class IndexStepImplCustom extends io.sapl.grammar.sapl.impl.IndexStepImpl {
	private static final String INDEX_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing an JSON array index [%s] expects array value, but got: '%s'.";
	private static final String INDEX_ACCESS_NOT_FOUND = "Index not found. Failed to access index [%s].";

	private static final int HASH_PRIME_07 = 41;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode)
			throws PolicyEvaluationException {
		if (!previousResult.getNode().isArray()) {
			throw new PolicyEvaluationException(
					String.format(INDEX_ACCESS_TYPE_MISMATCH, getIndex(), previousResult.getNode().getNodeType()));
		}
		int index = getIndex().toBigInteger().intValue();
		if (index < 0) {
			index += previousResult.getNode().size();
		}
		if (index < 0 || index >= previousResult.getNode().size()) {
			throw new PolicyEvaluationException(String.format(INDEX_ACCESS_NOT_FOUND, index));
		}
		return new JsonNodeWithParentArray(previousResult.getNode().get(index), previousResult.getNode(), index);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		int index = getIndex().toBigInteger().intValue();
		if (index < 0) {
			index += previousResult.getNodes().size();
		}
		if (index < 0 || index >= previousResult.getNodes().size()) {
			throw new PolicyEvaluationException(String.format(INDEX_ACCESS_NOT_FOUND, index));
		}
		return previousResult.getNodes().get(index);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getIndex());
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
		final IndexStepImplCustom otherImpl = (IndexStepImplCustom) other;
		return Objects.equals(getIndex(), otherImpl.getIndex());
	}

}
