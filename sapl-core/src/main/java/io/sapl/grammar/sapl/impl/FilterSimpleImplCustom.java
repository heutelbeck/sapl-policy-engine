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

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

public class FilterSimpleImplCustom extends io.sapl.grammar.sapl.impl.FilterSimpleImpl {

	private static final int INIT_PRIME_03 = 7;
	private static final int HASH_PRIME_07 = 41;

	@Override
	public JsonNode apply(JsonNode unfilteredRootNode, EvaluationContext ctx, JsonNode relativeNode)
			throws PolicyEvaluationException {
		String function = String.join(".", fsteps);
		return applyFilterStatement(unfilteredRootNode.deepCopy(), function, getArguments(), null, each, ctx,
				relativeNode);
	}

	@Override
	public Flux<JsonNode> reactiveApply(JsonNode unfilteredRootNode, EvaluationContext ctx, JsonNode relativeNode) {
		// TODO
		try {
			return Flux.just(apply(unfilteredRootNode, ctx, relativeNode));
		}
		catch (PolicyEvaluationException e) {
			return Flux.error(e);
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_03;
		hash = HASH_PRIME_07 * hash + ((getArguments() == null) ? 0 : getArguments().hash(imports));
		hash = HASH_PRIME_07 * hash + Objects.hashCode(getClass().getTypeName());
		for (String fStep : getFsteps()) {
			hash = HASH_PRIME_07 * hash + Objects.hashCode(fStep);
		}
		hash = HASH_PRIME_07 * hash + Objects.hashCode(isEach());
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
		final FilterSimpleImplCustom otherImpl = (FilterSimpleImplCustom) other;
		if ((getArguments() == null) ? (getArguments() != otherImpl.getArguments())
				: !getArguments().isEqualTo(otherImpl.getArguments(), otherImports, imports)) {
			return false;
		}
		if (!Objects.equals(isEach(), otherImpl.isEach())) {
			return false;
		}
		if (getFsteps().size() != otherImpl.getFsteps().size()) {
			return false;
		}
		ListIterator<String> left = getFsteps().listIterator();
		ListIterator<String> right = otherImpl.getFsteps().listIterator();
		while (left.hasNext()) {
			String lhs = left.next();
			String rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		return true;
	}

}
