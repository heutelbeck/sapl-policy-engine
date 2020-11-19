package io.sapl.interpreter.combinators;

import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;

public class DocumentsCombinatorFactory {

	public static DocumentsCombinator getCombinator(PolicyDocumentCombiningAlgorithm algorithm) {
		if (algorithm == PERMIT_UNLESS_DENY)
			return new PermitUnlessDenyCombinator();
		if (algorithm == PERMIT_OVERRIDES)
			return new PermitOverridesCombinator();
		if (algorithm == DENY_OVERRIDES)
			return new DenyOverridesCombinator();
		if (algorithm == ONLY_ONE_APPLICABLE)
			return new OnlyOneApplicableCombinator();

		return new DenyUnlessPermitCombinator();
	}
}
