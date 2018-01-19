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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.ArraySlicingStep;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;

public class ArraySlicingStepImplCustom extends ArraySlicingStepImpl {

	private static final String STEP_ZERO = "Step must not be zero.";
	private static final String INDEX_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing an JSON array index [%s] expects array value, but got: '%s'.";

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
		ArrayList<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		List<Integer> nodeIndices = resolveIndex(previousResult.getNode(), this);
		for (Integer i : nodeIndices) {
			list.add(new JsonNodeWithParentArray(previousResult.getNode().get(i), previousResult.getNode(), i));
		}
		return new ArrayResultNode(list);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		ArrayList<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		List<Integer> nodeIndices = resolveIndex(previousResult.asJsonWithoutAnnotations(), this);
		for (Integer i : nodeIndices) {
			list.add(previousResult.getNodes().get(i));
		}
		return new ArrayResultNode(list);
	}

	private static List<Integer> resolveIndex(TreeNode value, ArraySlicingStep indexStep)
			throws PolicyEvaluationException {
		BigDecimal step = indexStep.getStep() == null ? BigDecimal.ONE : indexStep.getStep();
		if (step.compareTo(BigDecimal.ZERO) == 0) {
			throw new PolicyEvaluationException(STEP_ZERO);
		}

		BigDecimal index = indexStep.getIndex() == null ? null : indexStep.getIndex();
		if(index != null && index.compareTo(BigDecimal.ZERO) < 0) {
			index = index.add(BigDecimal.valueOf(value.size()));
		}
		BigDecimal to = indexStep.getTo() == null ? null : indexStep.getTo();
		if(to != null && to.compareTo(BigDecimal.ZERO) < 0) {
			to = to.add(BigDecimal.valueOf(value.size()));
		}
		
		List<Integer> returnIndices = new ArrayList<>();
		if(step.compareTo(BigDecimal.ZERO) > 0) {
			index = index == null ? BigDecimal.ZERO : index;
			to = to == null ? BigDecimal.valueOf(value.size()) : to;
			if(index.compareTo(to) < 0) {
				for(int i=index.intValue();i<to.intValue();i=i+step.intValue()) {
					returnIndices.add(i);
				}
			}
		} else {
			index = index == null ? BigDecimal.valueOf(value.size()-1L) : index;
			to = to == null ? BigDecimal.valueOf(-1) : to;
			if(index.compareTo(to) > 0) {
				for(int i=index.intValue();i>to.intValue();i=i+step.intValue()) {
					returnIndices.add(i);
				}
			}
		}
		return returnIndices;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getIndex());
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getStep());
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getTo());
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
		final ArraySlicingStepImplCustom otherImpl = (ArraySlicingStepImplCustom) other;
		if (!Objects.equals(getIndex(), otherImpl.getIndex())) {
			return false;
		}
		if (!Objects.equals(getStep(), otherImpl.getStep())) {
			return false;
		}
		return Objects.equals(getTo(), otherImpl.getTo());
	}

}
