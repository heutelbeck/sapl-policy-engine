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
package io.sapl.grammar.sapl.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an array slicing step to a previous array
 * value, e.g. 'arr[4:12:2]'.
 *
 * Grammar: Step: '[' Subscript ']' ;
 *
 * Subscript returns Step: {ArraySlicingStep} index=JSONNUMBER? ':'
 * to=JSONNUMBER? (':' step=JSONNUMBER)? ;
 */
public class ArraySlicingStepImplCustom extends ArraySlicingStepImpl {

	private static final String STEP_ZERO = "Step must not be zero.";

	private static final String INDEX_ACCESS_TYPE_MISMATCH = "Type mismatch. Accessing an JSON array index [%s] expects array value, but got: '%s'.";

	@Override
	public Flux<ResultNode> apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(AbstractAnnotatedJsonNode previousResult) throws PolicyEvaluationException {
		if (!previousResult.getNode().isPresent() || !previousResult.getNode().get().isArray()) {
			throw new PolicyEvaluationException(INDEX_ACCESS_TYPE_MISMATCH, getIndex(),
					previousResult.getNode().isPresent() ? previousResult.getNode().get().getNodeType() : "undefined");
		}

		final List<Integer> nodeIndices = resolveIndex(previousResult.getNode().get());
		final List<AbstractAnnotatedJsonNode> list = new ArrayList<>(nodeIndices.size());
		for (Integer idx : nodeIndices) {
			list.add(new JsonNodeWithParentArray(Optional.of(previousResult.getNode().get().get(idx)),
					previousResult.getNode(), idx));
		}
		return new ArrayResultNode(list);
	}

	@Override
	public Flux<ResultNode> apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			Optional<JsonNode> relativeNode) {
		try {
			return Flux.just(apply(previousResult));
		} catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	private ResultNode apply(ArrayResultNode previousResult) throws PolicyEvaluationException {
		final List<Integer> nodeIndices = resolveIndex(previousResult.asJsonWithoutAnnotations()
				.orElseThrow(() -> new PolicyEvaluationException("undefined value")));
		final List<AbstractAnnotatedJsonNode> list = new ArrayList<>(nodeIndices.size());
		for (Integer i : nodeIndices) {
			list.add(previousResult.getNodes().get(i));
		}
		return new ArrayResultNode(list);
	}

	private List<Integer> resolveIndex(TreeNode value) throws PolicyEvaluationException {
		final BigDecimal step = getStep() == null ? BigDecimal.ONE : getStep();
		if (step.compareTo(BigDecimal.ZERO) == 0) {
			throw new PolicyEvaluationException(STEP_ZERO);
		}

		BigDecimal index = getIndex();
		if (index != null && index.compareTo(BigDecimal.ZERO) < 0) {
			index = index.add(BigDecimal.valueOf(value.size()));
		}

		BigDecimal to = getTo();
		if (to != null && to.compareTo(BigDecimal.ZERO) < 0) {
			to = to.add(BigDecimal.valueOf(value.size()));
		}

		final List<Integer> returnIndices = new ArrayList<>();
		if (step.compareTo(BigDecimal.ZERO) > 0) {
			index = index == null ? BigDecimal.ZERO : index;
			to = to == null ? BigDecimal.valueOf(value.size()) : to;
			if (index.compareTo(to) < 0) {
				for (int i = index.intValue(); i < to.intValue(); i = i + step.intValue()) {
					returnIndices.add(i);
				}
			}
		} else {
			index = index == null ? BigDecimal.valueOf(value.size() - 1L) : index;
			to = to == null ? BigDecimal.valueOf(-1) : to;
			if (index.compareTo(to) > 0) {
				for (int i = index.intValue(); i > to.intValue(); i = i + step.intValue()) {
					returnIndices.add(i);
				}
			}
		}
		return returnIndices;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = 17;
		hash = 37 * hash + Objects.hashCode(getClass().getTypeName());
		hash = 37 * hash + Objects.hashCode(getIndex());
		hash = 37 * hash + Objects.hashCode(getStep());
		hash = 37 * hash + Objects.hashCode(getTo());
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
