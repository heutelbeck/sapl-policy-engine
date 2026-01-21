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
package io.sapl.compiler.pdp;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.ast.Outcome;
import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SaplDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Map;
import java.util.function.Supplier;

@UtilityClass
public class PdpCompiler {

    public static CompiledPdpVoter compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx) {
        Outcome outcome = Outcome.PERMIT_OR_DENY;
        boolean hasConstraints = true;
        val voterMetadata = new PdpVoterMetadata("pdp voter",
                pdpConfiguration.pdpId(),
                pdpConfiguration.pdpId(),
                pdpConfiguration.combiningAlgorithm(),
                outcome, hasConstraints);
        Voter pdpVoter,
        Map<String, Value> variables,
        AttributeBroker attributeBroker,
        FunctionBroker functionBroker,
        Supplier<String> timestampSupplier
        throw new SaplCompilerException("PDP configuration not implemented %s %s".formatted(pdpConfiguration, ctx));
    }

    public static CompiledDocument compileDocument(SaplDocument saplDocument, CompilationContext ctx) {
        ctx.resetForNextDocument();
        return switch (saplDocument) {
        case Policy policy       -> PolicyCompiler.compilePolicy(policy, ctx);
        case PolicySet policySet -> PolicySetCompiler.compilePolicySet(policySet, ctx);
        };
    }
}
