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
package io.sapl.compiler.policy;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.Voter;

public record CompiledPolicy(
        CompiledExpression isApplicable,
        Voter voter,
        Voter applicabilityAndVote,
        CoverageVoter coverageVoter,
        VoterMetadata metadata) implements CompiledDocument {

    @Override
    public VoteResult evaluateApplicabilityError(ErrorValue error, EvaluationContext ctx) {
        if (voter instanceof PolicyCompiler.PolicyBodyVoter body) {
            return body.evaluateWithApplicability(error, ctx);
        }
        return CompiledDocument.super.evaluateApplicabilityError(error, ctx);
    }
}
