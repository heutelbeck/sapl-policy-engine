package io.sapl.prp.inmemory.indexed;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public interface IndexContainer {

	PolicyRetrievalResult match(final FunctionContext functionCtx,
			final VariableContext variableCtx);

}
