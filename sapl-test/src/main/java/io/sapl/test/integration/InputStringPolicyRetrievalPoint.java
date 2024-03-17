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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.SaplTestException;
import io.sapl.test.utils.DocumentHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InputStringPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private final Map<String, Document> documents;

    private final SAPLInterpreter saplInterpreter;

    InputStringPolicyRetrievalPoint(final Collection<String> documentStrings, final SAPLInterpreter interpreter) {
        this.saplInterpreter = interpreter;
        this.documents       = readPoliciesFromSaplDocumentNames(documentStrings);
    }

    private Map<String, Document> readPoliciesFromSaplDocumentNames(final Collection<String> documentStrings) {
        if (documentStrings == null || documentStrings.isEmpty()) {
            return Collections.emptyMap();
        }

        if (documentStrings.stream().anyMatch(documentString -> documentString == null || documentString.isEmpty())) {
            throw new SaplTestException("Encountered invalid policy input");
        }
        var docs = new HashMap<String, Document>();
        for (var documentString : documentStrings) {
            var document = DocumentHelper.readSaplDocumentFromInputString(documentString, saplInterpreter);
            var previous = docs.put(document.name(), document);
            if (previous != null) {
                throw new SaplTestException("Encountered policy name duplication '" + document.name() + "'.");
            }
        }
        return docs;
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        var documentMatches = Flux
                .merge(documents.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());
        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    @Override
    public Collection<Document> allDocuments() {
        return documents.values();
    }

    @Override
    public boolean isConsistent() {
        return true;
    }
}
