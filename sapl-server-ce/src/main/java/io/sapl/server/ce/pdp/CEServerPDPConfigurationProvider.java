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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Maps;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinatorFactory;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.embedded.config.PDPConfiguration;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.service.pdpconfiguration.CombiningAlgorithmService;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

@Component
@RequiredArgsConstructor
public class CEServerPDPConfigurationProvider implements PDPConfigurationProvider, PDPConfigurationPublisher {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private final Object locker = new Object();

	@Autowired
	private AttributeContext attributeCtx;
	@Autowired
	private FunctionContext functionCtx;
	@Autowired
	private CombiningAlgorithmService combiningAlgorithmService;
	@Autowired
	private VariablesService variableService;

	@Getter
	private Flux<DocumentsCombinator> documentsCombinator;
	@Getter
	private Flux<Map<String, JsonNode>> variables;

	private FluxSink<PolicyDocumentCombiningAlgorithm> documentCombiningAlgorithmFluxSink;
	private FluxSink<Collection<Variable>> variableFluxSink;

	private Disposable monitorAlgorithm;
	private Disposable monitorVariables;

	@PostConstruct
	public void init() {
		this.initFluxes();
		this.sendCurrentConfiguration();
	}

	@Override
	public Flux<PDPConfiguration> pdpConfiguration() {
		return Flux.combineLatest(Flux.from(documentsCombinator), Flux.from(variables), this::createConfiguration);
	}

	private PDPConfiguration createConfiguration(DocumentsCombinator combinator, Map<String, JsonNode> variables) {
		return new PDPConfiguration(new EvaluationContext(attributeCtx, functionCtx, variables), combinator);
	}

	@Override
	public void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm) {
		this.documentCombiningAlgorithmFluxSink.next(algorithm);
	}

	@Override
	public void publishVariables(@NonNull Collection<Variable> variables) {
		this.variableFluxSink.next(variables);
	}

	@PreDestroy
	public void destroy() {
		this.monitorVariables.dispose();
		this.monitorAlgorithm.dispose();
	}

	private static Map<String, JsonNode> generateVariablesAsMap(@NonNull Collection<Variable> variables) {
		Map<String, JsonNode> variablesAsMap = Maps.newHashMapWithExpectedSize(variables.size());
		for (Variable variable : variables) {
			variablesAsMap.put(variable.getName(), JSON.textNode(variable.getJsonValue()));
		}

		return variablesAsMap;
	}

	private void initFluxes() {
		synchronized (locker) {
			// @formatter:off
			ReplayProcessor<PolicyDocumentCombiningAlgorithm> combiningAlgorithmProcessor = ReplayProcessor
					.<PolicyDocumentCombiningAlgorithm>create();
			documentCombiningAlgorithmFluxSink = combiningAlgorithmProcessor.sink();
			documentsCombinator = combiningAlgorithmProcessor.map(
					DocumentsCombinatorFactory::getCombinator)
					.share().cache();
			monitorAlgorithm = documentsCombinator.subscribe();

			ReplayProcessor<Collection<Variable>> variablesProcessor = ReplayProcessor.<Collection<Variable>>create();
			variableFluxSink = variablesProcessor.sink();
			variables = variablesProcessor.map(CEServerPDPConfigurationProvider::generateVariablesAsMap)
					.share().cache();
			monitorVariables = variables.subscribe();
			// @formatter:on			
		}
	}

	private void sendCurrentConfiguration() {
		PolicyDocumentCombiningAlgorithm combiningAlgorithm = this.combiningAlgorithmService.getSelected();
		this.publishCombiningAlgorithm(combiningAlgorithm);

		Collection<Variable> variables = this.variableService.getAll();
		this.publishVariables(variables);
	}

	@Override
	public void dispose() {
		if (!monitorAlgorithm.isDisposed())
			monitorAlgorithm.dispose();
		if (!monitorVariables.isDisposed())
			monitorVariables.dispose();
	}

}
