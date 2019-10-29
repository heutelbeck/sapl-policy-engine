package io.sapl.interpreter.combinators;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;

/**
 * Interface which provides a method for obtaining a combined authorization decision for
 * the evaluation of multiple SAPL documents.
 */
public interface DocumentsCombinator {

	/**
	 * Method which evaluates multiple SAPL documents (containing a policy set or a
	 * policy) against an authorization subscription object, combines the results and
	 * creates and returns a corresponding authorization decision object. The method is
	 * supposed to be used to determine an authorization decision for the SAPL documents
	 * known to the PDP.
	 *
	 * Imports are obtained from the SAPL document.
	 * @param matchingSaplDocuments the SAPL documents
	 * @param errorsInTarget true if there was an error evaluating the document's target
	 * expression. A combining algorithm may make use of this information
	 * @param authzSubscription the authorization subscription object
	 * @param attributeCtx the attribute context
	 * @param functionCtx the function context
	 * @param systemVariables the system variables
	 * @return a {@link Flux} of {@link AuthorizationDecision} objects containing the
	 * combined decision, the combined obligation and advice and a transformed resource if
	 * applicable. A new authorization decision object is only pushed if it is different
	 * from the previous one.
	 */
	Flux<AuthorizationDecision> combineMatchingDocuments(Collection<SAPL> matchingSaplDocuments, boolean errorsInTarget,
			AuthorizationSubscription authzSubscription, AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables);

}
