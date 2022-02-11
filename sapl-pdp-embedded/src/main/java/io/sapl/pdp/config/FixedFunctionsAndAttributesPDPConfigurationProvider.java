/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.config;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class FixedFunctionsAndAttributesPDPConfigurationProvider implements PDPConfigurationProvider {

	private final AttributeContext attributeCtx;

	private final FunctionContext functionCtx;

	private final VariablesAndCombinatorSource variablesAndCombinatorSource;

	@Override
	public Flux<PDPConfiguration> pdpConfiguration() {
		return Flux.combineLatest(variablesAndCombinatorSource.getCombiningAlgorithm(),
				variablesAndCombinatorSource.getVariables(), this::createConfiguration);
	}

	private PDPConfiguration createConfiguration(
			Optional<CombiningAlgorithm> combinator,
			Optional<Map<String, JsonNode>> variables) {
		return new PDPConfiguration(attributeCtx, functionCtx, variables.orElse(null), combinator.orElse(null));
	}

	@Override
	public void dispose() {
		variablesAndCombinatorSource.dispose();
	}

}
