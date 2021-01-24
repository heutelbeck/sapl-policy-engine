/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.pdp;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Maps;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinatorFactory;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;

@Component
@RequiredArgsConstructor
public class CEVariablesAndCombinatorSource implements VariablesAndCombinatorSource, PDPConfigurationPublisher {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private Many<Collection<Variable>> variablesProcessorSink;
	private Many<PolicyDocumentCombiningAlgorithm> combiningAlgorithmSink;

	@PostConstruct
	public void init() {
		variablesProcessorSink = Sinks.many().replay().all();
		combiningAlgorithmSink = Sinks.many().replay().all();
	}

	@Override
	public Flux<Optional<Map<String, JsonNode>>> getVariables() {
		//@formatter:off
		return variablesProcessorSink.asFlux()
				.map(CEVariablesAndCombinatorSource::variablesCollentionToMap)
				.map(Optional::of);
		//@formatter:on
	}

	@Override
	public Flux<Optional<DocumentsCombinator>> getDocumentsCombinator() {
		//@formatter:off
		return combiningAlgorithmSink.asFlux()
				.map(DocumentsCombinatorFactory::getCombinator)
				.map(Optional::of);
		//@formatter:on
	}

	@Override
	public void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm) {
		combiningAlgorithmSink.emitNext(algorithm, EmitFailureHandler.FAIL_FAST);
	}

	@Override
	public void publishVariables(@NonNull Collection<Variable> variables) {
		variablesProcessorSink.emitNext(variables, EmitFailureHandler.FAIL_FAST);
	}

	@Override
	@PreDestroy
	public void dispose() {
	}

	private static Map<String, JsonNode> variablesCollentionToMap(@NonNull Collection<Variable> variables) {
		Map<String, JsonNode> variablesAsMap = Maps.newHashMapWithExpectedSize(variables.size());
		for (Variable variable : variables) {
			variablesAsMap.put(variable.getName(), JSON.textNode(variable.getJsonValue()));
		}

		return variablesAsMap;
	}
}
