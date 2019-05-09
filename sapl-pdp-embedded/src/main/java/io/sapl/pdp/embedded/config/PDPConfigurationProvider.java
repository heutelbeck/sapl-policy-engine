package io.sapl.pdp.embedded.config;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import reactor.core.publisher.Flux;

public interface PDPConfigurationProvider {

	Flux<DocumentsCombinator> getDocumentsCombinator();

	Flux<Map<String, JsonNode>> getVariables();

	default DocumentsCombinator convert(PolicyDocumentCombiningAlgorithm algorithm,
			SAPLInterpreter interpreter) {
		switch (algorithm) {
		case PERMIT_UNLESS_DENY:
			return new PermitUnlessDenyCombinator(interpreter);
		case DENY_UNLESS_PERMIT:
			return new DenyUnlessPermitCombinator(interpreter);
		case PERMIT_OVERRIDES:
			return new PermitOverridesCombinator(interpreter);
		case DENY_OVERRIDES:
			return new DenyOverridesCombinator(interpreter);
		case ONLY_ONE_APPLICABLE:
			return new OnlyOneApplicableCombinator(interpreter);
		default:
			throw new IllegalArgumentException(
					"Algorithm FIRST_APPLICABLE is not allowed for PDP level combination.");
		}
	}

}
