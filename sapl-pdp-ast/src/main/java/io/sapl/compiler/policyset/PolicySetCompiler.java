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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ReservedIdentifiers;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VarDef;
import io.sapl.compiler.combining.DenyOverridesCompiler;
import io.sapl.compiler.combining.DenyUnlessPermitCompiler;
import io.sapl.compiler.combining.FirstApplicableCompiler;
import io.sapl.compiler.combining.OnlyOneApplicableCompiler;
import io.sapl.compiler.combining.PermitOverridesCompiler;
import io.sapl.compiler.combining.PermitUnlessDenyCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policy.SchemaValidatorCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

@UtilityClass
public class PolicySetCompiler {

    public static final String ERROR_NO_POLICIES           = "Policy sets must contain at least one policy";
    public static final String ERROR_VARIABLE_REDEFINITION = "Redefinition of variable %s not permitted.";

    public static CompiledPolicySet compilePolicySet(PolicySet policySet, CompilationContext ctx) {
        compilePolicySetVariables(policySet, ctx);
        if (policySet.policies().isEmpty()) {
            throw new SaplCompilerException(ERROR_NO_POLICIES, policySet.location());
        }
        val metadata         = policySet.metadata();
        val compiledPolicies = policySet.policies().stream().map(policy -> PolicyCompiler.compilePolicy(policy, ctx))
                .toList();
        val schemaValidator  = SchemaValidatorCompiler.compileValidator(policySet.match(), ctx);
        val isApplicable     = TargetExpressionCompiler.compileTargetExpression(policySet.target(), schemaValidator,
                ctx);

        val decisionMakerAndCoverage = switch (policySet.algorithm()) {
        case DENY_OVERRIDES      ->
            DenyOverridesCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        case DENY_UNLESS_PERMIT  ->
            DenyUnlessPermitCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        case FIRST_APPLICABLE    ->
            FirstApplicableCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        case ONLY_ONE_APPLICABLE ->
            OnlyOneApplicableCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        case PERMIT_OVERRIDES    ->
            PermitOverridesCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        case PERMIT_UNLESS_DENY  ->
            PermitUnlessDenyCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable, metadata);
        };

        val applicabilityAndDecision = PolicySetUtil.compileApplicabilityAndDecision(isApplicable,
                decisionMakerAndCoverage.decisionMaker(), metadata);
        return new CompiledPolicySet(isApplicable, decisionMakerAndCoverage.decisionMaker(), applicabilityAndDecision,
                decisionMakerAndCoverage.coverage(), metadata);
    }

    private static void compilePolicySetVariables(PolicySet policySet, CompilationContext ctx) {
        for (VarDef variableDefinition : policySet.variables()) {
            val name = variableDefinition.name();
            if (ReservedIdentifiers.RESERVED_IDENTIFIERS.contains(name)) {
                throw new SaplCompilerException(ERROR_VARIABLE_REDEFINITION.formatted(name),
                        variableDefinition.location());
            }
            val compiledVariable = ExpressionCompiler.compile(variableDefinition.value(), ctx);
            if (!ctx.addGlobalPolicySetVariable(variableDefinition.name(), compiledVariable)) {
                throw new SaplCompilerException(ERROR_VARIABLE_REDEFINITION.formatted(name),
                        variableDefinition.location());
            }
        }
    }

}
