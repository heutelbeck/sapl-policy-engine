package io.sapl.server.ce.pdp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.combinators.DocumentsCombinator;
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
	private static JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

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
		Map<String, JsonNode> variablesAsMap = new HashMap<String, JsonNode>();
		for (Variable variable : variables) {
			variablesAsMap.put(variable.getName(), jsonNodeFactory.textNode(variable.getJsonValue()));
		}

		return variablesAsMap;
	}

	private void initFluxes() {
		// @formatter:off
		ReplayProcessor<PolicyDocumentCombiningAlgorithm> combiningAlgorithmProcessor = ReplayProcessor
				.<PolicyDocumentCombiningAlgorithm>create();
		this.documentCombiningAlgorithmFluxSink = combiningAlgorithmProcessor.sink();
		this.documentsCombinator = combiningAlgorithmProcessor
				.map((PolicyDocumentCombiningAlgorithm algorithm) -> this.convert(algorithm))
				.share()
				.cache();
		this.monitorAlgorithm = this.documentsCombinator.subscribe();

		ReplayProcessor<Collection<Variable>> variablesProcessor = ReplayProcessor.<Collection<Variable>>create();
		this.variableFluxSink = variablesProcessor.sink();
		this.variables = variablesProcessor
				.map((Collection<Variable> variables) -> generateVariablesAsMap(variables))
				.share()
				.cache();
		this.monitorVariables = this.variables.subscribe();
		// @formatter:on
	}

	private void sendCurrentConfiguration() {
		PolicyDocumentCombiningAlgorithm combiningAlgorithm = this.combiningAlgorithmService.getSelected();
		this.publishCombiningAlgorithm(combiningAlgorithm);

		Collection<Variable> variables = this.variableService.getAll();
		this.publishVariables(variables);
	}
}
