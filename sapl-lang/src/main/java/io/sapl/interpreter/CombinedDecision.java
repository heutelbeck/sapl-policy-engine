/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.Getter;
import lombok.ToString;

@ToString
public class CombinedDecision implements Traced {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Getter
    AuthorizationDecision                authorizationDecision;
    String                               combiningAlgorithm;
    LinkedList<DocumentEvaluationResult> documentEvaluationResults = new LinkedList<>();
    String                               errorMessage;

    static {
        MAPPER.registerModule(new Jdk8Module());
    }

    private CombinedDecision(AuthorizationDecision authorizationDecision, String combiningAlgorithm,
            List<DocumentEvaluationResult> documentEvaluationResults, String errorMessage) {
        this.authorizationDecision = authorizationDecision;
        this.combiningAlgorithm    = combiningAlgorithm;
        this.documentEvaluationResults.addAll(documentEvaluationResults);
        this.errorMessage = errorMessage;
    }

    public static CombinedDecision error(String errorMessage) {
        return new CombinedDecision(AuthorizationDecision.INDETERMINATE, "", List.of(), errorMessage);
    }

    public static CombinedDecision error(CombiningAlgorithm combiningAlgorithm, String errorMessage) {
        return new CombinedDecision(AuthorizationDecision.INDETERMINATE, combiningAlgorithm.toString(), List.of(),
                errorMessage);
    }

    public static CombinedDecision of(AuthorizationDecision authorizationDecision,
            CombiningAlgorithm combiningAlgorithm) {
        return new CombinedDecision(authorizationDecision, combiningAlgorithm.toString(), List.of(), null);
    }

    public static CombinedDecision of(AuthorizationDecision authorizationDecision,
            CombiningAlgorithm combiningAlgorithm, List<DocumentEvaluationResult> documentEvaluationResults) {
        return new CombinedDecision(authorizationDecision, combiningAlgorithm.toString(), documentEvaluationResults,
                null);
    }

    public static CombinedDecision error(PolicyDocumentCombiningAlgorithm combiningAlgorithm, String errorMessage) {
        return new CombinedDecision(AuthorizationDecision.INDETERMINATE, combiningAlgorithm.toString(), List.of(),
                errorMessage);
    }

    public static CombinedDecision of(AuthorizationDecision authorizationDecision,
            PolicyDocumentCombiningAlgorithm combiningAlgorithm) {
        return new CombinedDecision(authorizationDecision, combiningAlgorithm.toString(), List.of(), null);
    }

    public static CombinedDecision of(AuthorizationDecision authorizationDecision,
            PolicyDocumentCombiningAlgorithm combiningAlgorithm,
            List<DocumentEvaluationResult> documentEvaluationResults) {
        return new CombinedDecision(authorizationDecision, combiningAlgorithm.toString(), documentEvaluationResults,
                null);
    }

    public CombinedDecision withEvaluationResult(DocumentEvaluationResult result) {
        final var newCombinedDecision = new CombinedDecision(authorizationDecision, combiningAlgorithm,
                documentEvaluationResults, errorMessage);
        newCombinedDecision.documentEvaluationResults.add(result);
        return newCombinedDecision;
    }

    public CombinedDecision withDecisionAndEvaluationResult(AuthorizationDecision newAuthorizationDecision,
            DocumentEvaluationResult result) {
        final var newCombinedDecision = new CombinedDecision(newAuthorizationDecision, combiningAlgorithm,
                documentEvaluationResults, errorMessage);
        newCombinedDecision.documentEvaluationResults.add(result);
        return newCombinedDecision;
    }

    @Override
    public JsonNode getTrace() {
        final var trace = Val.JSON.objectNode();
        trace.set(Trace.COMBINING_ALGORITHM, Val.JSON.textNode(combiningAlgorithm));
        trace.set(Trace.AUTHORIZATION_DECISION, MAPPER.valueToTree(getAuthorizationDecision()));
        if (errorMessage != null) {
            trace.set(Trace.ERROR_MESSAGE, Val.JSON.textNode(errorMessage));
        }
        trace.set(Trace.EVALUATED_POLICIES, listOfTracedToJsonArray(documentEvaluationResults));
        return trace;
    }

    private JsonNode listOfTracedToJsonArray(List<DocumentEvaluationResult> results) {
        final var arrayNode = Val.JSON.arrayNode();
        results.forEach(r -> arrayNode.add(r.getTrace()));
        return arrayNode;
    }

    @Override
    public Collection<Val> getErrorsFromTrace() {
        final var errors = new ArrayList<Val>();
        if (errorMessage != null) {
            errors.add(ErrorFactory.error(errorMessage));
        }
        for (var result : documentEvaluationResults) {
            errors.addAll(result.getErrorsFromTrace());
        }
        return errors;
    }

}
