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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.ast.Outcome;
import io.sapl.compiler.combining.PriorityVoteCompiler;
import io.sapl.compiler.combining.UnanimousVoteCompiler;
import io.sapl.compiler.combining.UniqueVoteCompiler;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.sapl.compiler.document.DocumentCompiler.compileDocument;

@UtilityClass
public class PdpCompiler {

    public static final String ERROR_FIRST_NOT_ALLOWED = "FIRST is not allowed as combining algorithm option on PDP level as it implies an ordering that is not present here. Got: %s.";
    public static final String ERROR_NAME_COLLISION    = "Name collision during compilation of PDP. The policy or policy set with the name \"%s\" is defined at least twice.";

    private static CompiledPdpVoter illegalConfigurationVoter(ErrorValue error, PdpVoterMetadata voterMetadata,
            CompilationContext ctx) {
        val errorVote      = Vote.error(error, voterMetadata);
        val coverage       = new Coverage.PolicySetCoverage(voterMetadata, Coverage.BLANK_TARGET_HIT, List.of());
        val coverageStream = Flux.just(new VoteWithCoverage(errorVote, coverage));
        return new CompiledPdpVoter(voterMetadata, errorVote, coverageStream, ctx.getAttributeBroker(),
                ctx.getFunctionBroker(), ctx.getTimestampSupplier());
    }

    /**
     * Compiles a PDP configuration into an executable voter.
     *
     * @param pdpConfiguration the PDP configuration containing documents and
     * combining algorithm
     * @param ctx the compilation context with function and attribute brokers
     * @return compiled PDP voter, or an INDETERMINATE voter on configuration errors
     */
    public static CompiledPdpVoter compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx) {
        val voterMetadata = new PdpVoterMetadata("pdp voter", pdpConfiguration.pdpId(), pdpConfiguration.pdpId(),
                pdpConfiguration.combiningAlgorithm(), Outcome.PERMIT_OR_DENY, true);

        val compiledDocuments = new ArrayList<CompiledDocument>(pdpConfiguration.saplDocuments().size());
        for (val saplDocument : pdpConfiguration.saplDocuments()) {
            try {
                compiledDocuments.add(compileDocument(saplDocument, ctx));
            } catch (SaplCompilerException e) {
                return illegalConfigurationVoter(Value.error(e.getMessage()), voterMetadata, ctx);
            }
        }

        val nameCollision = findNameCollision(compiledDocuments);
        if (nameCollision != null) {
            val error = Value.error(ERROR_NAME_COLLISION.formatted(nameCollision));
            return illegalConfigurationVoter(error, voterMetadata, ctx);
        }

        val algorithm       = pdpConfiguration.combiningAlgorithm();
        val defaultDecision = algorithm.defaultDecision();
        val errorHandling   = algorithm.errorHandling();

        val voter = switch (algorithm.votingMode()) {
        case FIRST            ->
            Vote.error(Value.error(ERROR_FIRST_NOT_ALLOWED.formatted(algorithm.votingMode())), voterMetadata);
        case PRIORITY_DENY    -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata, Decision.DENY,
                defaultDecision, errorHandling);
        case PRIORITY_PERMIT  -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata, Decision.PERMIT,
                defaultDecision, errorHandling);
        case UNANIMOUS        ->
            UnanimousVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling, false);
        case UNANIMOUS_STRICT ->
            UnanimousVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling, true);
        case UNIQUE           ->
            UniqueVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling);
        };

        val coverageStream = switch (algorithm.votingMode()) {
        case FIRST            -> {
            val errorVote = Vote.error(Value.error(ERROR_FIRST_NOT_ALLOWED.formatted(algorithm.votingMode())),
                    voterMetadata);
            val coverage  = new Coverage.PolicySetCoverage(voterMetadata, Coverage.BLANK_TARGET_HIT, List.of());
            yield Flux.just(new VoteWithCoverage(errorVote, coverage));
        }
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

        return new CompiledPdpVoter(voterMetadata, voter, coverageStream, ctx.getAttributeBroker(),
                ctx.getFunctionBroker(), ctx.getTimestampSupplier());
    }

    private static String findNameCollision(List<? extends CompiledDocument> compiledDocuments) {
        val usedNames = HashSet.newHashSet(compiledDocuments.size());
        for (val compiledPolicy : compiledDocuments) {
            val name = compiledPolicy.metadata().name();
            if (!usedNames.add(name)) {
                return name;
            }
        }
        return null;
    }

}
