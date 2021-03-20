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
package io.sapl.interpreter.combinators;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.prp.PolicyRetrievalResult;
import reactor.core.publisher.Flux;

class CombinatorMockUtil {
	public static PolicyRetrievalResult mockPolicyRetrievalResult(boolean errorsInTarget,
			AuthorizationDecision... authorizationDecisions) {
		var documents = new ArrayList<AuthorizationDecisionEvaluable>(authorizationDecisions.length);
		for (var decision : authorizationDecisions) {
			documents.add(mockDocumentEvaluatingTo(decision));
		}
		return new PolicyRetrievalResult(documents, errorsInTarget, true);
	}

	public static AuthorizationDecisionEvaluable mockDocumentEvaluatingTo(AuthorizationDecision authzDecison) {
		var document = mock(AuthorizationDecisionEvaluable.class);
		when(document.evaluate(any())).thenReturn(Flux.just(authzDecison));
		return document;
	}
}
