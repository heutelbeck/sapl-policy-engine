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
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.ResultNode;

public class RecursiveWildcardStepImplCustom extends io.sapl.grammar.sapl.impl.RecursiveWildcardStepImpl {

	private static final int HASH_PRIME_11 = 59;
	private static final int INIT_PRIME_01 = 3;
	private static final String WRONG_TYPE = "Recursive descent step can only be applied to an object or an array.";
	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		if (!previousResult.getNode().isArray() && !previousResult.getNode().isObject()) {
			throw new PolicyEvaluationException(WRONG_TYPE);
		}
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		resultList.addAll(resolveRecursive(previousResult.getNode()));
		return new ArrayResultNode(resultList);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode)
			throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		for (AbstractAnnotatedJsonNode child : previousResult) {
			resultList.add(child);
			resultList.addAll(resolveRecursive(child.getNode()));
		}
		return new ArrayResultNode(resultList);
	}

	private static ArrayList<AbstractAnnotatedJsonNode> resolveRecursive(JsonNode node) {
		ArrayList<AbstractAnnotatedJsonNode> resultList = new ArrayList<>();
		if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				resultList.add(new JsonNodeWithParentArray(node.get(i), node, i));
				resultList.addAll(resolveRecursive(node.get(i)));
			}
		} else {
			Iterator<String> it = node.fieldNames();
			while (it.hasNext()) {
				String key = it.next();
				resultList.add(new JsonNodeWithParentObject(node.get(key), node, key));
				resultList.addAll(resolveRecursive(node.get(key)));
			}
		}
		return resultList;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_11 * hash + Objects.hashCode(getClass().getTypeName());
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		return !(other == null || getClass() != other.getClass());
	}

}
