package io.sapl.pdp.embedded.config;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import lombok.Value;

@Value
public class PDPConfiguration {
	EvaluationContext pdpScopedEvaluationContext;
	DocumentsCombinator documentsCombinator;
}
