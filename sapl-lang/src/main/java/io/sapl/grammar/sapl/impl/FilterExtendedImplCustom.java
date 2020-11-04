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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.DependentStreamsUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.FluxProvider;
import reactor.core.publisher.Flux;

public class FilterExtendedImplCustom extends FilterExtendedImpl {

	@Override
	public Flux<Val> apply(Val unfilteredRootNode, EvaluationContext ctx, Val relativeNode) {
		final JsonNode result = unfilteredRootNode.get().deepCopy();
		if (statements != null && !statements.isEmpty()) {
			final List<FluxProvider<Val>> fluxProviders = new ArrayList<>(statements.size());
			for (FilterStatement statement : statements) {
				final String function = String.join(".", statement.getFsteps());
				fluxProviders.add(node -> applyFilterStatement(node, statement.getTarget().getSteps(),
						statement.isEach(), function, statement.getArguments(), ctx, relativeNode));
			}
			return DependentStreamsUtil.nestedSwitchMap(Val.of(result), fluxProviders);
		} else {
			return Flux.just(Val.of(result));
		}
	}

}
