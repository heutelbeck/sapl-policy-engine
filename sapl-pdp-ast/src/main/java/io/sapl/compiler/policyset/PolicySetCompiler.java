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
package io.sapl.compiler.policyset;

import io.sapl.api.model.PureOperator;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.ast.DocumentType;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policy.PolicyMetadata;
import io.sapl.compiler.target.TargetExpressionCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;

import static io.sapl.compiler.combining.DenyOverridesCompiler.compileDenyOverridesSet;
import static io.sapl.compiler.combining.DenyUnlessPermitCompiler.compileDenyUnlessPermitSet;
import static io.sapl.compiler.combining.FirstApplicableCompiler.compileFirstApplicableSet;
import static io.sapl.compiler.combining.OnlyOneApplicableCompiler.compileOnlyOneApplicableSet;
import static io.sapl.compiler.combining.PermitOverridesCompiler.compilePermitOverridesSet;
import static io.sapl.compiler.combining.PermitUnlessDenyCompiler.compilePermitUnlessDenySet;

@UtilityClass
public class PolicySetCompiler {

    public static final String ERROR_NO_POLICIES = "Policy sets must contain at least one policy";

    public static CompiledPolicySet compilePolicySet(PolicySet policySet, PureOperator schemaValidator,
            CompilationContext ctx) {
        val compiledTarget = TargetExpressionCompiler.compileTargetExpression(policySet.target(), schemaValidator, ctx);
        val algorithm      = policySet.algorithm();
        val policies       = policySet.policies().stream().map(p -> PolicyCompiler.compilePolicy(p, null, ctx))
                .toList();
        if (policies.isEmpty()) {
            throw new SaplCompilerException(ERROR_NO_POLICIES, policySet.location());
        }
        val decisionSource = new PolicyMetadata(DocumentType.POLICY_SET, policySet.name(), policySet.pdpId(),
                policySet.configurationId(), policySet.documentId(), algorithm);
        return switch (algorithm) {
        case DENY_OVERRIDES      -> compileDenyOverridesSet(compiledTarget, decisionSource, policies, ctx);
        case DENY_UNLESS_PERMIT  -> compileDenyUnlessPermitSet(compiledTarget, decisionSource, policies, ctx);
        case FIRST_APPLICABLE    -> compileFirstApplicableSet(compiledTarget, decisionSource, policies, ctx);
        case ONLY_ONE_APPLICABLE -> compileOnlyOneApplicableSet(compiledTarget, decisionSource, policies, ctx);
        case PERMIT_OVERRIDES    -> compilePermitOverridesSet(compiledTarget, decisionSource, policies, ctx);
        case PERMIT_UNLESS_DENY  -> compilePermitUnlessDenySet(compiledTarget, decisionSource, policies, ctx);
        };
    }

}
