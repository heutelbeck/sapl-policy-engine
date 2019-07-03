package io.sapl.pdp.embedded.config;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

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

	default DocumentsCombinator convert(PolicyDocumentCombiningAlgorithm algorithm) {
		switch (algorithm) {
		case PERMIT_UNLESS_DENY:
			return new PermitUnlessDenyCombinator();
		case DENY_UNLESS_PERMIT:
			return new DenyUnlessPermitCombinator();
		case PERMIT_OVERRIDES:
			return new PermitOverridesCombinator();
		case DENY_OVERRIDES:
			return new DenyOverridesCombinator();
		case ONLY_ONE_APPLICABLE:
			return new OnlyOneApplicableCombinator();
		default:
			throw new IllegalArgumentException(
					"Algorithm FIRST_APPLICABLE is not allowed for PDP level combination.");
		}
	}

}
