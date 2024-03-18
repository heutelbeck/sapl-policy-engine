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
package io.sapl.prp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.sapl.api.interpreter.Val;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class PolicyRetrievalResult implements Serializable {

    private static final long serialVersionUID = -1433376078602499899L;

    ArrayList<DocumentMatch> matchingDocuments    = new ArrayList<>();
    ArrayList<DocumentMatch> nonMatchingDocuments = new ArrayList<>();
    @Getter
    private boolean          prpInconsistent      = false;
    @Getter
    private boolean          retrievalWithErrors  = false;
    @Getter
    private String           errorMessage;

    public PolicyRetrievalResult(List<DocumentMatch> matches, boolean retrievalWithErrors) {
        matches.forEach(this::withMatch);
        this.retrievalWithErrors = retrievalWithErrors;
    }

    public PolicyRetrievalResult withMatch(DocumentMatch match) {
        var targetExpressionResult = match.targetExpressionResult();
        if (targetExpressionResult.isError()) {
            retrievalWithErrors = true;
            nonMatchingDocuments.add(match);
        } else if (targetExpressionResult.getBoolean()) {
            // Can never be non-Boolean because this is caught in MatchingUtl where
            // non-Boolean is turned into an error already.
            matchingDocuments.add(match);
        } else {
            nonMatchingDocuments.add(match);
        }
        return this;
    }

    public static PolicyRetrievalResult invalidPrpResult() {
        var result = new PolicyRetrievalResult();
        result.prpInconsistent     = true;
        result.retrievalWithErrors = false;
        return result;
    }

    public static PolicyRetrievalResult retrievalErrorResult(String errorMessage) {
        var result = new PolicyRetrievalResult();
        result.errorMessage        = errorMessage;
        result.prpInconsistent     = false;
        result.retrievalWithErrors = true;
        return result;
    }

    public List<DocumentMatch> getMatchingDocuments() {
        return Collections.unmodifiableList(matchingDocuments);
    }

    public List<DocumentMatch> getNonMatchingDocuments() {
        return Collections.unmodifiableList(nonMatchingDocuments);
    }

    public List<Val> getErrors() {
        var errors = new ArrayList<Val>();
        if (errorMessage != null) {
            errors.add(Val.error(PolicyRetrievalPoint.class, errorMessage));
        }
        for (var match : nonMatchingDocuments) {
            errors.addAll(match.targetExpressionResult().getErrorsFromTrace());
        }
        return errors;
    }
}
