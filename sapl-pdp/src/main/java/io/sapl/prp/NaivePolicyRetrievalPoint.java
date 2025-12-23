/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.MatchingDocuments;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.api.prp.RetrievalError;
import io.sapl.compiler.CompiledPolicy;
import lombok.val;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NaivePolicyRetrievalPoint implements PolicyRetrievalPoint {

    private static final String ERROR_UNEXPECTED_TARGET_EXPRESSION_TYPE = "Unexpected target expression type in %s.";

    private final List<CompiledPolicy> alwaysApplicableDocuments;
    private final List<CompiledPolicy> maybeApplicableDocuments;

    public NaivePolicyRetrievalPoint(List<CompiledPolicy> alwaysApplicableDocuments,
            List<CompiledPolicy> maybeApplicableDocuments) {
        this.alwaysApplicableDocuments = List.copyOf(alwaysApplicableDocuments);
        this.maybeApplicableDocuments  = List.copyOf(maybeApplicableDocuments);
    }

    @Override
    public PolicyRetrievalResult getMatchingDocuments(AuthorizationSubscription authorizationSubscription,
            EvaluationContext evaluationContext) {
        val result         = new ArrayList<CompiledPolicy>(alwaysApplicableDocuments);
        val totalDocuments = alwaysApplicableDocuments.size() + maybeApplicableDocuments.size();
        for (var candidate : maybeApplicableDocuments) {
            if (candidate.matchExpression() instanceof PureExpression pureMatchExpression) {
                val match = pureMatchExpression.evaluate(evaluationContext);
                if (match instanceof ErrorValue error) {
                    return new RetrievalError(candidate.name(), error);
                }
                if (Value.TRUE.equals(match)) {
                    result.add(candidate);
                }
            } else {
                return new RetrievalError(candidate.name(),
                        Value.error(ERROR_UNEXPECTED_TARGET_EXPRESSION_TYPE.formatted(candidate.name())));
            }
        }
        return new MatchingDocuments(Collections.unmodifiableList(result), totalDocuments);
    }
}
