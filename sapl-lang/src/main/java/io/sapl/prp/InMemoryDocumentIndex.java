/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import reactor.core.publisher.Mono;

public interface InMemoryDocumentIndex {

	void insert(String documentKey, String document);

	void publish(String documentKey);

	void withdraw(String documentKey);

	void updateFunctionContext(FunctionContext functionCtx);

	void setLiveMode();

	Mono<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables);

}
