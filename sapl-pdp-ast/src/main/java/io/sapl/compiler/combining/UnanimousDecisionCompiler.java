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
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policyset.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

@UtilityClass
public class UnanimousDecisionCompiler {

    public static DecisionMakerAndCoverage compilePolicySet(PolicySet policySet, List<CompiledPolicy> compiledPolicies,
            CompiledExpression isApplicable, PolicySetMetadata metadata, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        val decisionMaker = compileDecisionMaker(compiledPolicies, metadata, policySet.location(), defaultDecision,
                errorHandling);
        val coverage      = compileCoverageStream(policySet, isApplicable, compiledPolicies, metadata, defaultDecision,
                errorHandling);
        return new DecisionMakerAndCoverage(decisionMaker, coverage);
    }

    private static Flux<PolicySetDecisionWithCoverage> compileCoverageStream(PolicySet policySet,
            CompiledExpression isApplicable, List<CompiledPolicy> compiledPolicies, PolicySetMetadata metadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s, %s".formatted(policySet, isApplicable,
                compiledPolicies, metadata, defaultDecision, errorHandling));
    }

    private static DecisionMaker compileDecisionMaker(List<CompiledPolicy> compiledPolicies, PolicySetMetadata metadata,
            @NonNull SourceLocation location, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s".formatted(compiledPolicies, metadata,
                location, defaultDecision, errorHandling));
    }

}
