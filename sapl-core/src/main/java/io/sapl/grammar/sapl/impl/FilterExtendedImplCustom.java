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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;

public class FilterExtendedImplCustom extends io.sapl.grammar.sapl.impl.FilterExtendedImpl {

	private static final int HASH_PRIME_12 = 61;
	private static final int INIT_PRIME_03 = 7;

	@Override
	public JsonNode apply(JsonNode unfilteredRootNode, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) throws PolicyEvaluationException {
		JsonNode result = unfilteredRootNode.deepCopy();

		for (FilterStatement statement : statements) {
			final String function = String.join(".", statement.getFsteps());
			result = applyFilterStatement(result, function, statement.getArguments(), statement.getTarget().getSteps(),
					statement.isEach(), ctx, isBody, relativeNode);
		}

		return result;
	}

	@Override
	public Flux<JsonNode> reactiveApply(JsonNode unfilteredRootNode, EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		final JsonNode result = unfilteredRootNode.deepCopy();
		if (statements != null && ! statements.isEmpty()) {
			final List<FluxProvider<JsonNode>> fluxProviders = new ArrayList<>(statements.size());
			for (FilterStatement statement : statements) {
                final String function = String.join(".", statement.getFsteps());
				fluxProviders.add(node -> reactiveApplyFilterStatement(node, function, statement.getArguments(), statement.getTarget().getSteps(),
						statement.isEach(), ctx, isBody, relativeNode));
			}
			return cascadingSwitchMap(result, fluxProviders, 0);
		} else {
			return Flux.just(result);
		}
	}

	private static Flux<JsonNode> cascadingSwitchMap(JsonNode input, List<FluxProvider<JsonNode>> fluxProviders, int idx) {
		if (idx < fluxProviders.size()) {
			return fluxProviders.get(idx).getFlux(input).switchMap(result -> cascadingSwitchMap(result, fluxProviders, idx + 1));
		}
		return Flux.just(input);
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_03;
		hash = HASH_PRIME_12 * hash + Objects.hashCode(getClass().getTypeName());
		for (FilterStatement statement : getStatements()) {
			hash = HASH_PRIME_12 * hash + ((statement == null) ? 0 : statement.hash(imports));
		}
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
		final FilterExtendedImplCustom otherImpl = (FilterExtendedImplCustom) other;
		if (getStatements().size() != otherImpl.getStatements().size()) {
			return false;
		}
		ListIterator<FilterStatement> left = getStatements().listIterator();
		ListIterator<FilterStatement> right = otherImpl.getStatements().listIterator();
		while (left.hasNext()) {
			FilterStatement lhs = left.next();
			FilterStatement rhs = right.next();
			if (!lhs.isEqualTo(rhs, otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
