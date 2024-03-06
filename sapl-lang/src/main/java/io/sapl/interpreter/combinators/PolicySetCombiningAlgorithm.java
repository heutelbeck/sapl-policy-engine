package io.sapl.interpreter.combinators;

import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import reactor.core.publisher.Flux;

@FunctionalInterface
public interface PolicySetCombiningAlgorithm {
    Flux<CombinedDecision> combinePoliciesInSet(PolicySet policySet);
}
