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

import io.sapl.api.model.*;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPolicy;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policyset.PolicySetBody;
import io.sapl.compiler.policyset.PolicySetDecision;
import io.sapl.compiler.policyset.PolicySetDecisionWithCoverage;
import io.sapl.compiler.policyset.PolicySetMetadata;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@UtilityClass
public class FirstApplicableCompiler {
    public static CompiledPolicySet compilePolicySet(PolicySet policySet, CompiledExpression targetExpression,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies, CompilationContext ctx) {

        val coverageStream = compilePolicySetCoverageStream(policySet, targetExpression, policySetMetadata, policies,
                ctx);

        val maybeShortCircuitBody = shortCircuitIfPredetermined(policySet, policySetMetadata, policies);
        if (maybeShortCircuitBody.isPresent()) {
            return new CompiledPolicySet(targetExpression, maybeShortCircuitBody.get(), coverageStream);
        }

        val maybePureBody = pureBodyIfPoliciesPure(policySet, policySetMetadata, policies);
        if (maybePureBody.isPresent()) {
            return new CompiledPolicySet(targetExpression, maybePureBody.get(), coverageStream);
        }

        return new CompiledPolicySet(targetExpression, streamBody(policySet, policySetMetadata, policies),
                coverageStream);
    }

    public static Flux<PolicySetDecisionWithCoverage> compilePolicySetCoverageStream(PolicySet policySet,
            CompiledExpression targetExpression, PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies,
            CompilationContext ctx) {
        return Flux.empty();
    }

    private static Optional<PolicySetBody> pureBodyIfPoliciesPure(PolicySet policySet,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies) {
        var hasStreams = false;
        for (CompiledPolicy policy : policies) {
            if (policy.targetExpression() instanceof StreamOperator) {
                hasStreams = true;
                break;
            }
        }
        return Optional.empty();
    }

    private static PolicySetBody streamBody(PolicySet policySet, PolicySetMetadata policySetMetadata,
            List<CompiledPolicy> policies) {
        throw new SaplCompilerException("FirstApplicableCompiler not yet implemented");

    }

    private static Optional<PolicySetBody> shortCircuitIfPredetermined(PolicySet policySet,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies) {
        PolicySetBody policySetBody = null;
        // Detect if we have a short-circuit
        for (CompiledPolicy policy : policies) {
            val policyTarget = policy.targetExpression();
            val policyBody   = policy.policyBody();
            if (policyTarget instanceof ErrorValue error) {
                policySetBody = PolicySetDecision.error(
                        Value.errorAt(policySet.target().location(), "Policy target returned an error."),
                        policySetMetadata, List.of());
            } else if (policyTarget instanceof BooleanValue(var t)) {
                if (t) {
                    if (policyBody instanceof PolicyDecision decision) {
                        policySetBody = PolicySetDecision.error(
                                Value.errorAt(policySet.target().location(), "Policy target returned an error."),
                                policySetMetadata, List.of());
                    }
                } else {
                    continue;
                }
            } else {
                break;
            }
        }
        return Optional.ofNullable(policySetBody);
    }

}
