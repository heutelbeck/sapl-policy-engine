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
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

@Component
@RequiredArgsConstructor
public class CEVariablesAndCombinatorSource implements VariablesAndCombinatorSource, PDPConfigurationPublisher {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Getter
	private Flux<Optional<DocumentsCombinator>> documentsCombinator;
	@Getter
	private Flux<Optional<Map<String, JsonNode>>> variables;

	private FluxSink<PolicyDocumentCombiningAlgorithm> documentCombiningAlgorithmFluxSink;
	private FluxSink<Collection<Variable>> variableFluxSink;

	private Disposable monitorAlgorithm;
	private Disposable monitorVariables;

	@PostConstruct
	public void init() {
		initAlgorithmFlux();
		initVariablesFlux();
	}

	private void initVariablesFlux() {
		ReplayProcessor<Collection<Variable>> variablesProcessor = ReplayProcessor.<Collection<Variable>>create();
		variableFluxSink = variablesProcessor.sink();
		variables = variablesProcessor.map(CEVariablesAndCombinatorSource::variablesCollentionToMap).map(Optional::of)
				.share().cache();
		monitorVariables = variables.subscribe();
	}

	private void initAlgorithmFlux() {
		ReplayProcessor<PolicyDocumentCombiningAlgorithm> combiningAlgorithmProcessor = ReplayProcessor
				.<PolicyDocumentCombiningAlgorithm>create();
		documentCombiningAlgorithmFluxSink = combiningAlgorithmProcessor.sink();
		documentsCombinator = combiningAlgorithmProcessor.map(DocumentsCombinatorFactory::getCombinator)
				.map(Optional::of).share().cache();
		monitorAlgorithm = documentsCombinator.subscribe();
	}

	@Override
	public void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm) {
		documentCombiningAlgorithmFluxSink.next(algorithm);
	}

	@Override
	public void publishVariables(@NonNull Collection<Variable> variables) {
		variableFluxSink.next(variables);
	}

	private static Map<String, JsonNode> variablesCollentionToMap(@NonNull Collection<Variable> variables) {
		Map<String, JsonNode> variablesAsMap = Maps.newHashMapWithExpectedSize(variables.size());
		for (Variable variable : variables) {
			variablesAsMap.put(variable.getName(), JSON.textNode(variable.getJsonValue()));
		}
		return variablesAsMap;
	}

	@Override
	@PreDestroy
	public void dispose() {
		if (!monitorAlgorithm.isDisposed())
			monitorAlgorithm.dispose();
		if (!monitorVariables.isDisposed())
			monitorVariables.dispose();
	}

}
