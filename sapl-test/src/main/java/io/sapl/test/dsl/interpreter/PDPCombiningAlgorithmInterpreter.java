package io.sapl.test.dsl.interpreter;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithmEnum;

public class PDPCombiningAlgorithmInterpreter {
    PolicyDocumentCombiningAlgorithm interpretPdpCombiningAlgorithm(final CombiningAlgorithmEnum combiningAlgorithm) {
        if (combiningAlgorithm == null) {
            throw new SaplTestException("CombiningAlgorithm is null");
        }

        return switch (combiningAlgorithm) {
            case DENY_OVERRIDES -> PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
            case PERMIT_OVERRIDES -> PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
            case ONLY_ONE_APPLICABLE -> PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
            case DENY_UNLESS_PERMIT -> PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
            case PERMIT_UNLESS_DENY -> PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
        };
    }
}
