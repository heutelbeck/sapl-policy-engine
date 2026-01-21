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

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.ast.Outcome;
import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SaplDocument;
import io.sapl.compiler.ast.SAPLCompiler;
import io.sapl.compiler.combining.PriorityVoteCompiler;
import io.sapl.compiler.combining.UnanimousVoteCompiler;
import io.sapl.compiler.combining.UniqueVoteCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import io.sapl.compiler.policyset.PolicySetUtil;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@UtilityClass
public class PdpCompiler {

    public static final String ERROR_NAME_COLLISION         = "Name collision during compilation of PDP. The policy or policy set with the name \"%s\" is defined at least twice.";
    public static final String ERROR_FIRST_IS_NOT_ALLOWED_S = "FIRST is not allowed as combining algorithm option on PDP level as it is implying an ordering that is not present here. Got: %s.";

    public static CompiledPdpVoter compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx) {
        Outcome outcome           = Outcome.PERMIT_OR_DENY;
        boolean hasConstraints    = true;
        var     voterMetadata     = new PdpVoterMetadata("pdp voter", pdpConfiguration.pdpId(),
                pdpConfiguration.pdpId(), pdpConfiguration.combiningAlgorithm(), outcome, hasConstraints);
        val     compiledDocuments = new ArrayList<CompiledDocument>(pdpConfiguration.saplDocuments().size());
        for (val saplDocument : pdpConfiguration.saplDocuments()) {
            val parsedDocument = SAPLCompiler.parseDocument(saplDocument);
            if (parsedDocument.isInvalid()) {
                System.err.println("Parsing of SAPL document failed: %s.".formatted(parsedDocument.syntaxErrors()));
                throw new SaplCompilerException(
                        "Parsing of SAPL document failed: %s.".formatted(parsedDocument.syntaxErrors()));
            }
            compiledDocuments.add(compileDocument(parsedDocument.saplDocument(), ctx));
        }
        assertDocumentNamesAreUnique(compiledDocuments);
        val algorithm = pdpConfiguration.combiningAlgorithm();

        val defaultDecision = algorithm.defaultDecision();
        val errorHandling   = algorithm.errorHandling();
        val voter           = switch (algorithm.votingMode()) {
                            case FIRST            -> throw new SaplCompilerException(
                                    ERROR_FIRST_IS_NOT_ALLOWED_S.formatted(algorithm.votingMode()));
                            case PRIORITY_DENY    -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata,
                                    Decision.DENY, defaultDecision, errorHandling);
                            case PRIORITY_PERMIT  -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata,
                                    Decision.PERMIT, defaultDecision, errorHandling);
                            case UNANIMOUS        -> UnanimousVoteCompiler.compileVoter(compiledDocuments,
                                    voterMetadata, defaultDecision, errorHandling, false);
                            case UNANIMOUS_STRICT -> UnanimousVoteCompiler.compileVoter(compiledDocuments,
                                    voterMetadata, defaultDecision, errorHandling, true);
                            case UNIQUE           -> UniqueVoteCompiler.compileVoter(compiledDocuments, voterMetadata,
                                    defaultDecision, errorHandling);
                            };

        val coverageStream = switch (algorithm.votingMode()) {
        case FIRST            ->
            throw new SaplCompilerException(ERROR_FIRST_IS_NOT_ALLOWED_S.formatted(algorithm.votingMode()));
        case PRIORITY_DENY    -> PriorityVoteCompiler.compileCoverageStream(compiledDocuments, voterMetadata,
                Decision.DENY, defaultDecision, errorHandling);
        case PRIORITY_PERMIT  -> PriorityVoteCompiler.compileCoverageStream(compiledDocuments, voterMetadata,
                Decision.PERMIT, defaultDecision, errorHandling);
        case UNANIMOUS        -> UnanimousVoteCompiler.compileCoverageStream(compiledDocuments, voterMetadata,
                defaultDecision, errorHandling, false);
        case UNANIMOUS_STRICT -> UnanimousVoteCompiler.compileCoverageStream(compiledDocuments, voterMetadata,
                defaultDecision, errorHandling, true);
        case UNIQUE           ->
            UniqueVoteCompiler.compileCoverageStream(compiledDocuments, voterMetadata, defaultDecision, errorHandling);
        };
        return new CompiledPdpVoter(voterMetadata, voter, coverageStream, pdpConfiguration.variables(),
                ctx.getAttributeBroker(), ctx.getFunctionBroker(), ctx.getTimestampSupplier());
    }

    private static void assertDocumentNamesAreUnique(List<? extends CompiledDocument> compiledDocuments) {
        val usedNames = new HashSet<>(compiledDocuments.size());
        for (val compiledPolicy : compiledDocuments) {
            val name = compiledPolicy.metadata().name();
            if (!usedNames.add(name)) {
                throw new SaplCompilerException(ERROR_NAME_COLLISION.formatted(name));
            }
        }
    }

    public static CompiledDocument compileDocument(SaplDocument saplDocument, CompilationContext ctx) {
        ctx.resetForNextDocument();
        return switch (saplDocument) {
        case Policy policy       -> PolicyCompiler.compilePolicy(policy, ctx);
        case PolicySet policySet -> PolicySetCompiler.compilePolicySet(policySet, ctx);
        };
    }
}
