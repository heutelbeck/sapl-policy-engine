package io.sapl.test.dsl.interpreter;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.DenyOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.OnlyOneApplicableCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitUnlessDenyCombiningAlgorithm;

public class PDPCombiningAlgorithmInterpreter {
    PolicyDocumentCombiningAlgorithm interpretPdpCombiningAlgorithm(final CombiningAlgorithm combiningAlgorithm) {
        if (combiningAlgorithm instanceof DenyOverridesCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
        } else if (combiningAlgorithm instanceof PermitOverridesCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
        } else if (combiningAlgorithm instanceof OnlyOneApplicableCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
        } else if (combiningAlgorithm instanceof PermitUnlessDenyCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
        } else {
            return PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
        }
    }
}
