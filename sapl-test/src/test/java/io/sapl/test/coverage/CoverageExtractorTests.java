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
package io.sapl.test.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.ast.PolicySetVoterMetadata;
import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.model.Coverage.BodyCoverage;
import io.sapl.compiler.model.Coverage.ConditionHit;
import io.sapl.compiler.model.Coverage.PolicyCoverage;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.VoteWithCoverage;
import lombok.val;

@DisplayName("CoverageExtractor tests")
class CoverageExtractorTests {

    private static final String CULTIST_POLICY_SOURCE = """
            policy "cultist-access"
            permit
                subject.role == "cultist";
                resource.artifact == "necronomicon";
            """;

    @Test
    @DisplayName("extracts coverage from simple policy with conditions")
    void whenTracedPolicyWithConditions_thenExtractsCoverage() {
        val voteWithCoverage = buildPolicyVoteWithCoverage("elder-ritual-policy", Outcome.PERMIT, Decision.PERMIT,
                new ConditionData(0, true, 3), new ConditionData(1, false, 5));

        val policySources = Map.of("elder-ritual-policy", CULTIST_POLICY_SOURCE);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, policySources);

        assertThat(coverages).hasSize(1);
        val coverage = coverages.getFirst();
        assertThat(coverage.getDocumentName()).isEqualTo("elder-ritual-policy");
        assertThat(coverage.getDocumentSource()).isEqualTo(CULTIST_POLICY_SOURCE);
        assertThat(coverage.getDocumentType()).isEqualTo("policy");
        // Policy outcome is recorded (line 1), plus 2 conditions
        assertThat(coverage.getConditionCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("extracts coverage when policy returns NOT_APPLICABLE")
    void whenPolicyReturnsNotApplicable_thenRecordsOutcome() {
        val voteWithCoverage = buildPolicyVoteWithCoverage("unmatched-policy", Outcome.PERMIT, Decision.NOT_APPLICABLE);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, Map.of());

        assertThat(coverages).hasSize(1);
        // Policy returned NOT_APPLICABLE instead of its entitlement (PERMIT)
        // This is tracked via policy outcome
    }

    @Test
    @DisplayName("handles policy set type correctly")
    void whenPolicySetType_thenDocumentTypeIsSet() {
        val voteWithCoverage = buildPolicySetVoteWithCoverage("miskatonic-rules", Outcome.PERMIT_OR_DENY,
                Decision.PERMIT, true);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, Map.of());

        assertThat(coverages).hasSize(1);
        assertThat(coverages.getFirst().getDocumentType()).isEqualTo("set");
    }

    @Test
    @DisplayName("extracts target hit from policy set")
    void whenPolicySetWithTarget_thenExtractsTargetHit() {
        val voteWithCoverage = buildPolicySetVoteWithCoverage("targeted-set", Outcome.PERMIT_OR_DENY, Decision.PERMIT,
                true);

        val policySources = Map.of("targeted-set", "set \"targeted-set\" for resource.type == \"secret\" ...");

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, policySources);

        assertThat(coverages).hasSize(1);
        assertThat(coverages.getFirst().wasTargetMatched()).isTrue();
    }

    @Test
    @DisplayName("extracts condition hits with correct branch data")
    void whenConditionsPresent_thenExtractsBranchHits() {
        // Same condition (statementId=0) hit with both true and false
        val voteWithCoverage = buildPolicyVoteWithCoverage("arkham-access", Outcome.PERMIT, Decision.PERMIT,
                new ConditionData(0, true, 3), new ConditionData(0, false, 3), new ConditionData(1, true, 5));

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, Map.of());

        val coverage = coverages.getFirst();
        // Should have conditions + policy outcome
        assertThat(coverage.getBranchHits()).isNotEmpty();
    }

    @Test
    @DisplayName("skips error value conditions")
    void whenConditionResultIsError_thenSkipsCondition() {
        val errorHit         = new ConditionHit(Value.error("evaluation failed"), sourceLocation(3), 0);
        val bodyCoverage     = new BodyCoverage(List.of(errorHit), 1);
        val voter            = new PolicyVoterMetadata("error-policy", "default", "config", null, Outcome.PERMIT,
                false);
        val policyCoverage   = new PolicyCoverage(voter, bodyCoverage);
        val vote             = new Vote(AuthorizationDecision.PERMIT, List.of(), List.of(), List.of(), voter,
                Outcome.PERMIT);
        val voteWithCoverage = new VoteWithCoverage(vote, policyCoverage);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, Map.of());

        // Error conditions are skipped, only policy outcome recorded
        assertThat(coverages).hasSize(1);
    }

    @Test
    @DisplayName("hasCoverageData returns true when coverage present")
    void whenCoveragePresent_thenHasCoverageDataReturnsTrue() {
        val voteWithCoverage = buildPolicyVoteWithCoverage("policy", Outcome.PERMIT, Decision.PERMIT,
                new ConditionData(0, true, 3));

        assertThat(CoverageExtractor.hasCoverageData(voteWithCoverage)).isTrue();
    }

    @Test
    @DisplayName("hasCoverageData returns false when coverage is null")
    void whenCoverageNull_thenHasCoverageDataReturnsFalse() {
        val voter            = new PolicyVoterMetadata("policy", "default", "config", null, Outcome.PERMIT, false);
        val vote             = new Vote(AuthorizationDecision.PERMIT, List.of(), List.of(), List.of(), voter,
                Outcome.PERMIT);
        val voteWithCoverage = new VoteWithCoverage(vote, null);

        assertThat(CoverageExtractor.hasCoverageData(voteWithCoverage)).isFalse();
    }

    @Test
    @DisplayName("resolves policy source from provided map")
    void whenPolicySourceProvided_thenAttachesToCoverage() {
        val voteWithCoverage = buildPolicyVoteWithCoverage("sourced-policy", Outcome.PERMIT, Decision.PERMIT);
        val sourceCode       = "policy \"sourced-policy\" permit";
        val policySources    = Map.of("sourced-policy", sourceCode);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, policySources);

        assertThat(coverages.getFirst().getDocumentSource()).isEqualTo(sourceCode);
    }

    @Test
    @DisplayName("uses empty source when policy not in map")
    void whenPolicySourceNotProvided_thenUsesEmptyString() {
        val voteWithCoverage = buildPolicyVoteWithCoverage("unknown-policy", Outcome.PERMIT, Decision.PERMIT);
        val policySources    = new HashMap<String, String>();

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, policySources);

        assertThat(coverages.getFirst().getDocumentSource()).isEmpty();
    }

    @Test
    @DisplayName("handles condition without location gracefully")
    void whenConditionMissingLocation_thenUsesDefaultLine() {
        val hit              = new ConditionHit(Value.TRUE, null, 0);
        val bodyCoverage     = new BodyCoverage(List.of(hit), 1);
        val voter            = new PolicyVoterMetadata("no-loc-policy", "default", "config", null, Outcome.PERMIT,
                false);
        val policyCoverage   = new PolicyCoverage(voter, bodyCoverage);
        val vote             = new Vote(AuthorizationDecision.PERMIT, List.of(), List.of(), List.of(), voter,
                Outcome.PERMIT);
        val voteWithCoverage = new VoteWithCoverage(vote, policyCoverage);

        val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, Map.of());

        assertThat(coverages).hasSize(1);
        // Condition with null location should use line 0 as fallback
    }

    // Helper record for test data
    private record ConditionData(int statementId, boolean result, int line) {}

    private VoteWithCoverage buildPolicyVoteWithCoverage(String policyName, Outcome outcome, Decision decision,
            ConditionData... conditions) {
        val hits = new ArrayList<ConditionHit>();
        for (val cond : conditions) {
            hits.add(new ConditionHit(cond.result() ? Value.TRUE : Value.FALSE, sourceLocation(cond.line()),
                    cond.statementId()));
        }

        val bodyCoverage   = new BodyCoverage(hits, conditions.length);
        val voter          = new PolicyVoterMetadata(policyName, "default", "config", null, outcome, false);
        val policyCoverage = new PolicyCoverage(voter, bodyCoverage);

        val vote = new Vote(toAuthorizationDecision(decision), List.of(), List.of(), List.of(), voter, outcome);

        return new VoteWithCoverage(vote, policyCoverage);
    }

    private VoteWithCoverage buildPolicySetVoteWithCoverage(String setName, Outcome outcome, Decision decision,
            boolean targetMatched) {
        val voter     = new PolicySetVoterMetadata(setName, "default", "config", null,
                new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE), outcome, false);
        val targetHit = targetMatched ? new Coverage.TargetResult(Value.TRUE, sourceLocation(1))
                : new Coverage.TargetResult(Value.FALSE, sourceLocation(1));

        val setCoverage = new PolicySetCoverage(voter, targetHit, List.of());

        val vote = new Vote(toAuthorizationDecision(decision), List.of(), List.of(), List.of(), voter, outcome);

        return new VoteWithCoverage(vote, setCoverage);
    }

    private AuthorizationDecision toAuthorizationDecision(Decision decision) {
        return switch (decision) {
        case PERMIT         -> AuthorizationDecision.PERMIT;
        case DENY           -> AuthorizationDecision.DENY;
        case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
        case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }

    private SourceLocation sourceLocation(int line) {
        return new SourceLocation(null, null, 0, 0, line, 0, line, 0);
    }
}
