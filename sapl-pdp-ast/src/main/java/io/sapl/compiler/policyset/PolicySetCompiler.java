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

import io.sapl.api.model.ReservedIdentifiers;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VarDef;
import io.sapl.compiler.combining.FirstVoteCompiler;
import io.sapl.compiler.combining.PriorityVoteCompiler;
import io.sapl.compiler.combining.UnanimousDecisionCompiler;
import io.sapl.compiler.combining.UniqueDecisionCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policy.SchemaValidatorCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;

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
        val algorithm        = policySet.algorithm();
        val defaultDecision  = algorithm.defaultDecision();
        val errorHandling    = algorithm.errorHandling();
        val voterAndCoverage = switch (algorithm.votingMode()) {
                             case DENY_WINS          ->
                                 PriorityVoteCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable,
                                         metadata, Decision.DENY, defaultDecision, errorHandling);
                             case PERMIT_WINS        ->
                                 PriorityVoteCompiler.compilePolicySet(policySet, compiledPolicies, isApplicable,
                                         metadata, Decision.PERMIT, defaultDecision, errorHandling);
                             case FIRST_VOTE         -> FirstVoteCompiler.compilePolicySet(policySet, compiledPolicies,
                                     isApplicable, metadata, defaultDecision, errorHandling);
                             case UNIQUE_DECISION    -> UniqueDecisionCompiler.compilePolicySet(policySet,
                                     compiledPolicies, isApplicable, metadata, defaultDecision, errorHandling);
                             case UNANIMOUS_DECISION -> UnanimousDecisionCompiler.compilePolicySet(policySet,
                                     compiledPolicies, isApplicable, metadata, defaultDecision, errorHandling);
                             };

        val applicabilityAndVoter = PolicySetUtil.compileApplicabilityAndVoter(isApplicable, voterAndCoverage.voter(),
                metadata);
        return new CompiledPolicySet(isApplicable, voterAndCoverage.voter(), applicabilityAndVoter,
                voterAndCoverage.coverage(), metadata);
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
