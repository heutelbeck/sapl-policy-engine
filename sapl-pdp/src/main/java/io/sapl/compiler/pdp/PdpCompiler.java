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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.ast.Outcome;
import io.sapl.compiler.combining.PriorityVoteCompiler;
import io.sapl.compiler.combining.UnanimousVoteCompiler;
import io.sapl.compiler.combining.UniqueVoteCompiler;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CoverageVoter;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static io.sapl.compiler.document.DocumentCompiler.compileDocument;

@UtilityClass
public class PdpCompiler {

    private static final String ERROR_FIRST_NOT_ALLOWED = "FIRST is not allowed as combining algorithm option on PDP level as it implies an ordering that is not present here. Got: %s.";
    private static final String ERROR_NAME_COLLISION    = "Name collision during compilation of PDP. The policy or policy set with the name \"%s\" is defined at least twice.";

    /**
     * Creates an error voter that returns INDETERMINATE for all authorization
     * requests. Used by {@link io.sapl.pdp.configuration.PdpVoterSource} when
     * compilation fails and no previous valid configuration exists.
     *
     * @param pdpConfiguration the PDP configuration that failed to compile
     * @param exception the compilation exception
     * @return a compiled voter that always returns INDETERMINATE
     */
    public static CompiledPdp createErrorVoter(PDPConfiguration pdpConfiguration, SaplCompilerException exception,
            PluginsBundle plugins) {
        val voterMetadata = new PdpVoterMetadata("pdp voter", pdpConfiguration.pdpId(), pdpConfiguration.pdpId(),
                pdpConfiguration.combiningAlgorithm(), Outcome.PERMIT_OR_DENY, true);
        val error         = Value.error(exception.getMessage());
        val errorVote     = Vote.error(error, voterMetadata);
        val coverageVoter = new ErrorPdpCoverageVoter(voterMetadata, errorVote);
        return new CompiledPdp(voterMetadata, errorVote, coverageVoter, plugins);
    }

    /**
     * Coverage voter for an error PDP configuration: yields the same error
     * vote on every evaluation, with empty PDP coverage (no documents
     * compiled successfully).
     */
    private record ErrorPdpCoverageVoter(PdpVoterMetadata voterMetadata, Vote errorVote) implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val voteResult = new VoteResult(errorVote, Map.of());
            val coverage   = new Coverage.PdpCoverage(voterMetadata, List.of());
            return new VoteResultWithCoverage(voteResult, coverage);
        }
    }

    /**
     * Compiles a PDP configuration into an executable voter.
     *
     * @param pdpConfiguration the PDP configuration containing documents and
     * combining algorithm
     * @param ctx the compilation context with function and attribute brokers
     * @return compiled PDP voter
     * @throws SaplCompilerException if any document fails to compile, document
     * names collide, or the FIRST combining algorithm is used at PDP level
     */
    public static CompiledPdp compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx) {
        return compilePDPConfiguration(pdpConfiguration, ctx, new PluginsBundle(ctx.getFunctionBroker()));
    }

    public static CompiledPdp compilePDPConfiguration(PDPConfiguration pdpConfiguration, CompilationContext ctx,
            PluginsBundle plugins) {
        val compiledDocuments = new ArrayList<CompiledDocument>(pdpConfiguration.saplDocuments().size());
        for (val saplDocument : pdpConfiguration.saplDocuments()) {
            compiledDocuments.add(compileDocument(saplDocument, ctx));
        }

        val nameCollision = findNameCollision(compiledDocuments);
        if (nameCollision != null) {
            throw new SaplCompilerException(ERROR_NAME_COLLISION.formatted(nameCollision));
        }

        val algorithm       = pdpConfiguration.combiningAlgorithm();
        val defaultDecision = algorithm.defaultDecision();
        val errorHandling   = algorithm.errorHandling();

        val outcome       = Outcome.union(defaultDecision,
                compiledDocuments.stream().map(CompiledDocument::outcome).toList());
        val voterMetadata = new PdpVoterMetadata("pdp voter", pdpConfiguration.pdpId(), pdpConfiguration.pdpId(),
                algorithm, outcome, true);

        val voter = switch (algorithm.votingMode()) {
        case FIRST            ->
            throw new SaplCompilerException(ERROR_FIRST_NOT_ALLOWED.formatted(algorithm.votingMode()));
        case PRIORITY_DENY    -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata, Decision.DENY,
                defaultDecision, errorHandling, ctx);
        case PRIORITY_PERMIT  -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata, Decision.PERMIT,
                defaultDecision, errorHandling, ctx);
        case PRIORITY_SUSPEND -> PriorityVoteCompiler.compileVoter(compiledDocuments, voterMetadata, Decision.SUSPEND,
                defaultDecision, errorHandling, ctx);
        case UNANIMOUS        -> UnanimousVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision,
                errorHandling, false, false, ctx);
        case UNANIMOUS_STRICT -> UnanimousVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision,
                errorHandling, true, false, ctx);
        case UNIQUE           -> UniqueVoteCompiler.compileVoter(compiledDocuments, voterMetadata, defaultDecision,
                errorHandling, false, ctx);
        };

        val coverageVoter = switch (algorithm.votingMode()) {
        case FIRST            ->
            throw new SaplCompilerException(ERROR_FIRST_NOT_ALLOWED.formatted(algorithm.votingMode()));
        case PRIORITY_DENY    -> PriorityVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                Decision.DENY, defaultDecision, errorHandling);
        case PRIORITY_PERMIT  -> PriorityVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                Decision.PERMIT, defaultDecision, errorHandling);
        case PRIORITY_SUSPEND -> PriorityVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                Decision.SUSPEND, defaultDecision, errorHandling);
        case UNANIMOUS        -> UnanimousVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                defaultDecision, errorHandling, false);
        case UNANIMOUS_STRICT -> UnanimousVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                defaultDecision, errorHandling, true);
        case UNIQUE           -> UniqueVoteCompiler.compilePdpCoverageVoter(compiledDocuments, voterMetadata,
                defaultDecision, errorHandling);
        };

        return new CompiledPdp(voterMetadata, voter, coverageVoter, plugins);
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
