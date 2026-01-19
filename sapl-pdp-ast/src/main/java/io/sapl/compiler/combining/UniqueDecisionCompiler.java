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
package io.sapl.compiler.combining;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.SourceLocation;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.Voter;
import io.sapl.compiler.pdp.CompiledDocument;
import io.sapl.compiler.pdp.VoteWithCoverage;
import io.sapl.ast.VoterMetadata;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

@UtilityClass
public class UniqueDecisionCompiler {
    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, policySet.location(), defaultDecision,
                errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, defaultDecision,
                errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s, %s".formatted(policySet, isApplicable,
                compiledPolicies, voterMetadata, defaultDecision, errorHandling));
    }

    private static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            @NonNull SourceLocation location, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s".formatted(compiledPolicies, voterMetadata,
                location, defaultDecision, errorHandling));
    }

}
