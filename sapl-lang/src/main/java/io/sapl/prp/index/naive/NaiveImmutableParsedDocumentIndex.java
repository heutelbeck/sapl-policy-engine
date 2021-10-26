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
package io.sapl.prp.index.naive;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * The Index Object has to be immutable to avoid race conditions. SAPL Objects are assumed
 * to be immutable.
 */
@Slf4j
@ToString
public class NaiveImmutableParsedDocumentIndex implements ImmutableParsedDocumentIndex {

	private final Map<String, SAPL> documentsByName;

	private final boolean consistent;

	public NaiveImmutableParsedDocumentIndex() {
		documentsByName = new HashMap<>();
		consistent = true;
	}

	private NaiveImmutableParsedDocumentIndex(Map<String, SAPL> documentsByName, boolean consistent) {
		this.documentsByName = documentsByName;
		this.consistent = consistent;
	}

	@Override
	public Mono<PolicyRetrievalResult> retrievePolicies(EvaluationContext subscriptionScopedEvaluationContext) {
		return retrievePoliciesCollector(subscriptionScopedEvaluationContext);
	}

	public Mono<PolicyRetrievalResult> retrievePoliciesCollector(
			EvaluationContext subscriptionScopedEvaluationContext) {
		if (!consistent)
			return Mono.just(new PolicyRetrievalResult().withInvalidState());

		var documentsWithMatchingInformation = Flux.merge(documentsByName.values().stream().map(
				document -> document.matches(subscriptionScopedEvaluationContext).map(val -> Tuples.of(document, val)))
				.collect(Collectors.toList()));

		// refactor inner lambda to function
		return documentsWithMatchingInformation.reduce(new PolicyRetrievalResult(),
				(policyRetrievalResult, documentWithMatchingInformation) -> {
					var match = documentWithMatchingInformation.getT2();
					if (match.isError())
						return policyRetrievalResult.withError();
					if (!match.isBoolean()) {
						log.error("matching returned error. (Should never happen): {}", match.getMessage());
						return policyRetrievalResult.withError();
					}
					if (match.getBoolean())
						return policyRetrievalResult.withMatch(documentWithMatchingInformation.getT1());

					return policyRetrievalResult;
				});
	}

	@Override
	public ImmutableParsedDocumentIndex apply(PrpUpdateEvent event) {
		// Do a shallow copy. String is immutable, and SAPL is assumed to be too.
		var newDocuments = new HashMap<>(documentsByName);
		var newConsistencyState = consistent;
		for (var update : event.getUpdates()) {
			if (update.getType() == Type.CONSISTENT) {
				newConsistencyState = true;
			}
			else if (update.getType() == Type.INCONSISTENT) {
				newConsistencyState = false;
			}
			else {
				applyUpdate(newDocuments, update);
			}
		}
		return new NaiveImmutableParsedDocumentIndex(newDocuments, newConsistencyState);
	}

	// only PUBLISH or UNPUBLISH
	private void applyUpdate(Map<String, SAPL> newDocuments, PrpUpdateEvent.Update update) {
		var name = update.getDocument().getPolicyElement().getSaplName();
		if (update.getType() == Type.UNPUBLISH) {
			newDocuments.remove(name);
		}
		else {
			newDocuments.put(name, update.getDocument());
		}
	}

}
