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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@Getter
@ToString
@EqualsAndHashCode
public class PolicySetDecision implements DocumentEvaluationResult {

    final CombinedDecision combinedDecision;
    final String           documentName;
    final Optional<Val>    targetResult;
    final Optional<String> errorMessage;

    private PolicySetDecision(CombinedDecision combinedDecision,
            String documentName,
            Optional<Val> matches,
            Optional<String> errorMessage) {
        this.combinedDecision = combinedDecision;
        this.documentName     = documentName;
        this.targetResult     = matches;
        this.errorMessage     = errorMessage;
    }

    public static PolicySetDecision of(CombinedDecision combinedDecision, String document) {
        return new PolicySetDecision(combinedDecision, document, Optional.empty(), Optional.empty());
    }

    public static PolicySetDecision error(String document, String errorMessage) {
        return new PolicySetDecision(null, document, Optional.empty(), Optional.ofNullable(errorMessage));
    }

    public static PolicySetDecision ofTargetError(String document, Val targetValue,
            CombiningAlgorithm combiningAlgorithm) {
        return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.INDETERMINATE, combiningAlgorithm),
                document, Optional.ofNullable(targetValue), Optional.empty());
    }

    public static PolicySetDecision notApplicable(String document, Val targetValue,
            CombiningAlgorithm combiningAlgorithm) {
        return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.NOT_APPLICABLE, combiningAlgorithm),
                document, Optional.ofNullable(targetValue), Optional.empty());
    }

    public static DocumentEvaluationResult ofImportError(String document, String errorMessage,
            CombiningAlgorithm combiningAlgorithm) {
        return new PolicySetDecision(CombinedDecision.of(AuthorizationDecision.INDETERMINATE, combiningAlgorithm),
                document, Optional.empty(), Optional.ofNullable(errorMessage));
    }

    @Override
    public DocumentEvaluationResult withTargetResult(Val targetResult) {
        return new PolicySetDecision(combinedDecision, documentName, Optional.ofNullable(targetResult), errorMessage);
    }

    @Override
    public AuthorizationDecision getAuthorizationDecision() {
        if (errorMessage.isPresent())
            return AuthorizationDecision.INDETERMINATE;

        return combinedDecision.getAuthorizationDecision();
    }

    @Override
    public JsonNode getTrace() {
        final var trace = Val.JSON.objectNode();
        trace.set(Trace.DOCUMENT_TYPE, Val.JSON.textNode("policy set"));
        trace.set(Trace.POLICY_SET_NAME, Val.JSON.textNode(documentName));
        if (combinedDecision != null)
            trace.set(Trace.COMBINED_DECISION, combinedDecision.getTrace());
        errorMessage.ifPresent(error -> trace.set(Trace.ERROR_MESSAGE, Val.JSON.textNode(errorMessage.get())));
        targetResult.ifPresent(target -> trace.set(Trace.TARGET, target.getTrace()));
        return trace;
    }

    @Override
    public Collection<Val> getErrorsFromTrace() {
        final var errors = new ArrayList<Val>();
        targetResult.ifPresent(target -> errors.addAll(target.getErrorsFromTrace()));
        errors.addAll(combinedDecision.getErrorsFromTrace());
        return errors;
    }

}
