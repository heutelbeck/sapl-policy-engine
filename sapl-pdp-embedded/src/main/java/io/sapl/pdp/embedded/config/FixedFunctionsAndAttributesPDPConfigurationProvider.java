package io.sapl.pdp.embedded.config;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.DocumentsCombinator;
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
		return Flux.combineLatest(variablesAndCombinatorSource.getDocumentsCombinator(),
				variablesAndCombinatorSource.getVariables(), this::createConfiguration);
	}

	private PDPConfiguration createConfiguration(DocumentsCombinator combinator, Map<String, JsonNode> variables) {
		return new PDPConfiguration(new EvaluationContext(attributeCtx, functionCtx, variables), combinator);
	}

	@Override
	public void dispose() {
		variablesAndCombinatorSource.dispose();
	}

}
