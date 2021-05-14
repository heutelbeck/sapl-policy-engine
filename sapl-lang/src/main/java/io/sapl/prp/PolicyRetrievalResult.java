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

import java.util.ArrayList;
import java.util.Collection;

import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRetrievalResult {

    Collection<? extends AuthorizationDecisionEvaluable> matchingDocuments = new ArrayList<>();
    @Getter
    boolean errorsInTarget = false;
    @Getter
    boolean prpValidState = true;

    public Collection<? extends AuthorizationDecisionEvaluable> getMatchingDocuments() {
        return this.matchingDocuments;
    }

    public PolicyRetrievalResult withMatch(AuthorizationDecisionEvaluable match) {
        var matches = new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments);
        matches.add(match);
        return new PolicyRetrievalResult(matches, errorsInTarget, prpValidState);
    }

    public PolicyRetrievalResult withError() {
        return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), true, prpValidState);
    }

    public PolicyRetrievalResult withInvalidState() {
        return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), errorsInTarget, false);
    }

}
