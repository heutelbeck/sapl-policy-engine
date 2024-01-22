/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.test.integration;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.SaplTestException;
import io.sapl.test.utils.DocumentHelper;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class InputStringPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private final Map<String, SAPL> documents;

    private final SAPLInterpreter saplInterpreter;

    InputStringPolicyRetrievalPoint(final Collection<String> documentStrings, final SAPLInterpreter interpreter) {
        this.saplInterpreter = interpreter;
        this.documents       = readPoliciesFromSaplDocumentNames(documentStrings);
    }

    private Map<String, SAPL> readPoliciesFromSaplDocumentNames(final Collection<String> documentStrings) {
        if (documentStrings == null || documentStrings.isEmpty()) {
            return Collections.emptyMap();
        }

        if (documentStrings.stream().anyMatch(documentString -> documentString == null || documentString.isEmpty())) {
            throw new SaplTestException("Encountered invalid policy input");
        }

        return documentStrings.stream()
                .map(documentString -> DocumentHelper.readSaplDocumentFromInputString(documentString, saplInterpreter))
                .collect(Collectors.toMap(sapl -> sapl.getPolicyElement().getSaplName(), Function.identity(),
                        (oldKey, newKey) -> newKey));
    }

    @Override
    public Flux<PolicyRetrievalResult> retrievePolicies() {
        var retrieval = Mono.just(new PolicyRetrievalResult());
        for (SAPL document : documents.values()) {
            retrieval = retrieval.flatMap(retrievalResult -> document.matches().map(match -> {
                if (match.isError()) {
                    return retrievalResult.withError();
                }
                if (match.getBoolean()) {
                    return retrievalResult.withMatch(document);
                }
                return retrievalResult;
            }));
        }

        return Flux.from(retrieval).doOnNext(this::logMatching);
    }

    private void logMatching(PolicyRetrievalResult result) {
        if (result.getMatchingDocuments().isEmpty()) {
            log.trace("|-- Matching documents: NONE");
        } else {
            log.trace("|-- Matching documents:");
            for (SAPL doc : result.getMatchingDocuments())
                log.trace("| |-- * {} ", doc);
        }
        log.trace("|");
    }
}
