/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
