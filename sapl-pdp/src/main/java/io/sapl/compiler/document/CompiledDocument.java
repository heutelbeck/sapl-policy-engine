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
package io.sapl.compiler.document;

import java.util.Map;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.policy.CoverageVoter;

public interface CompiledDocument {

    VoterMetadata metadata();

    default Outcome outcome() {
        return metadata().outcome();
    }

    CompiledExpression isApplicable();

    Voter voter();

    Voter applicabilityAndVote();

    /**
     * Resolves an indexed applicability error for this document.
     *
     * @param error the error already produced by the applicability condition
     * @param ctx the current evaluation context
     * @return the vote and dependencies of any remaining body conditions
     */
    default VoteResult evaluateApplicabilityError(ErrorValue error, EvaluationContext ctx) {
        return new VoteResult(Vote.error(error, metadata()), Map.of());
    }

    CoverageVoter coverageVoter();
}
