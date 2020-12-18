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
package io.sapl.prp.index;

import java.util.Map;

import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import reactor.core.publisher.Mono;

public interface ImmutableParsedDocumentIndex {

	Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext);

	ImmutableParsedDocumentIndex apply(PrpUpdateEvent event);

	default void applyEvent(Map<String, SAPL> newDocuments, PrpUpdateEvent event) {
		for (var update : event.getUpdates()) {
			applyUpdate(newDocuments, update);
		}
	}

	private void applyUpdate(Map<String, SAPL> newDocuments, Update update) {
		var name = update.getDocument().getPolicyElement().getSaplName();
		if (update.getType() == Type.UNPUBLISH) {
			newDocuments.remove(name);
		} else {
			if (newDocuments.containsKey(name)) {
				throw new RuntimeException("Fatal error. Policy name collision. A document with a name ('" + name
						+ "') identical to an existing document was published to the PRP.");
			}
			newDocuments.put(name, update.getDocument());
		}
	}

}