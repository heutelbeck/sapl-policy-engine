/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import reactor.core.publisher.Flux;

/**
 * A policy retrieval point is responsible for selecting all the policies
 * matching a given request.
 */
public interface PolicyRetrievalPoint {

	/**
	 * Returns a {@link Flux} of policy retrieval results containing all the
	 * policies or policy sets having a target expression that matches the given
	 * authorization subscription. The given function context and variables
	 * constitute the environment the target expressions are evaluated in.
	 * 
	 * @param authzSubscription the authorization subscription for which matching
	 *                          policies are to be retrieved.
	 * @param functionCtx       the function context being part of the target
	 *                          expression's evaluation environment.
	 * @param variables         the variables being part of the target expression's
	 *                          evaluation environment.
	 * @return a {@link Flux} providing the policy retrieval results containing all
	 *         the matching policies or policy sets. New results are only added to
	 *         the stream if they are different from the preceding result.
	 */
	Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables);

	void shutdown();
}
