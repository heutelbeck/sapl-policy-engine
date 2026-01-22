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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.compiler.model.Coverage.BodyCoverage;
import io.sapl.compiler.model.Coverage.ConditionHit;
import io.sapl.compiler.model.Coverage.PolicyCoverage;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.VoteWithCoverage;
import lombok.val;

@DisplayName("CoverageAccumulator tests")
class CoverageAccumulatorTests {

    @Test
    @DisplayName("initializes with test identifier")
    void whenCreated_thenHasTestIdentifier() {
        val accumulator = new CoverageAccumulator("ritual-invocation-test");

        assertThat(accumulator.getTestIdentifier()).isEqualTo("ritual-invocation-test");
        assertThat(accumulator.hasCoverage()).isFalse();
        assertThat(accumulator.getRecord()).isNotNull();
    }

    @Test
    @DisplayName("registers single policy source")
    void whenRegisterPolicySource_thenSourceAvailable() {
        val accumulator = new CoverageAccumulator("test");
        val source      = "policy \"necronomicon\" permit";

        accumulator.registerPolicySource("necronomicon", source);

        assertThat(accumulator.getPolicySources()).containsEntry("necronomicon", source);
    }

    @Test
    @DisplayName("registers multiple policy sources")
    void whenRegisterPolicySources_thenAllAvailable() {
        val accumulator = new CoverageAccumulator("test");
        val sources     = Map.of("cthulhu-policy", "policy \"cthulhu-policy\" permit", "dagon-policy",
                "policy \"dagon-policy\" deny");

        accumulator.registerPolicySources(sources);

        assertThat(accumulator.getPolicySources()).hasSize(2).containsKeys("cthulhu-policy", "dagon-policy");
    }

    @Test
    @DisplayName("records coverage from VoteWithCoverage")
    void whenRecordCoverage_thenCoverageAccumulated() {
        val accumulator = new CoverageAccumulator("test");

        val voteWithCoverage = buildVoteWithCoverage("elder-ritual", Decision.PERMIT, new ConditionData(0, true, 3));

        accumulator.recordCoverage(voteWithCoverage);

        assertThat(accumulator.hasCoverage()).isTrue();
        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getEvaluationCount()).isOne();
        assertThat(coverageRecord.getPolicyCount()).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.PERMIT)).isOne();
    }

    @Test
    @DisplayName("records coverage with policy source correlation")
    void whenRecordCoverageWithSource_thenSourceAttached() {
        val accumulator = new CoverageAccumulator("test");
        val source      = "policy \"summoning\" permit resource.type == \"scroll\";";

        accumulator.registerPolicySource("summoning", source);

        val voteWithCoverage = buildVoteWithCoverage("summoning", Decision.DENY, new ConditionData(0, false, 5));

        accumulator.recordCoverage(voteWithCoverage);

        val policyCoverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(policyCoverage.getDocumentSource()).isEqualTo(source);
    }

    @Test
    @DisplayName("accumulates multiple evaluations")
    void whenMultipleEvaluations_thenAllAccumulated() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.recordCoverage(buildVoteWithCoverage("policy1", Decision.PERMIT));
        accumulator.recordCoverage(buildVoteWithCoverage("policy2", Decision.DENY));
        accumulator.recordCoverage(buildVoteWithCoverage("policy1", Decision.PERMIT, new ConditionData(0, true, 3)));

        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getEvaluationCount()).isEqualTo(3);
        assertThat(coverageRecord.getPolicyCount()).isEqualTo(2);
        assertThat(coverageRecord.getDecisionCount(Decision.PERMIT)).isEqualTo(2);
        assertThat(coverageRecord.getDecisionCount(Decision.DENY)).isOne();
    }

    @Test
    @DisplayName("merges coverage for same policy across evaluations")
    void whenSamePolicyMultipleTimes_thenCoverageMerged() {
        val accumulator = new CoverageAccumulator("test");

        // First evaluation: true branch
        accumulator
                .recordCoverage(buildVoteWithCoverage("shared-policy", Decision.PERMIT, new ConditionData(0, true, 3)));

        // Second evaluation: false branch
        accumulator.recordCoverage(
                buildVoteWithCoverage("shared-policy", Decision.PERMIT, new ConditionData(0, false, 3)));

        val coverageRecord = accumulator.getRecord();
        val coverage       = coverageRecord.getPolicyCoverageList().getFirst();

        assertThat(coverageRecord.getPolicyCount()).isOne();
        // One condition fully covered (true + false branches)
        assertThat(coverage.getFullyCoveredConditionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("records errors separately")
    void whenRecordError_thenErrorCountIncremented() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.recordError();
        accumulator.recordError();

        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getErrorCount()).isEqualTo(2);
        assertThat(coverageRecord.getEvaluationCount()).isZero();
    }

    @Test
    @DisplayName("provides summary string")
    void whenGetSummary_thenReturnsFormattedSummary() {
        val accumulator = new CoverageAccumulator("arkham-test");

        accumulator.recordCoverage(buildVoteWithCoverage("policy1", Decision.PERMIT, new ConditionData(0, true, 3)));

        val summary = accumulator.getSummary();

        assertThat(summary).contains("arkham-test").contains("policies=1").contains("evaluations=1")
                .contains("branchCoverage=");
    }

    @Test
    @DisplayName("handles all decision types")
    void whenVariousDecisions_thenAllCounted() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.recordCoverage(buildVoteWithCoverage("p1", Decision.PERMIT));
        accumulator.recordCoverage(buildVoteWithCoverage("p2", Decision.DENY));
        accumulator.recordCoverage(buildVoteWithCoverage("p3", Decision.INDETERMINATE));
        accumulator.recordCoverage(buildVoteWithCoverage("p4", Decision.NOT_APPLICABLE));

        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getDecisionCount(Decision.PERMIT)).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.DENY)).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.INDETERMINATE)).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.NOT_APPLICABLE)).isOne();
    }

    @Test
    @DisplayName("registers single policy file path")
    void whenRegisterPolicyFilePath_thenFilePathSetOnCoverage() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.registerPolicyFilePath("elder-policy", "policies/elder-access.sapl");

        val voteWithCoverage = buildVoteWithCoverage("elder-policy", Decision.PERMIT);
        accumulator.recordCoverage(voteWithCoverage);

        val coverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(coverage.getFilePath()).isEqualTo("policies/elder-access.sapl");
    }

    @Test
    @DisplayName("registers multiple policy file paths")
    void whenRegisterPolicyFilePaths_thenAllFilePathsSetOnCoverage() {
        val accumulator = new CoverageAccumulator("test");
        val filePaths   = Map.of("cthulhu-policy", "policies/cthulhu.sapl", "dagon-policy", "policies/dagon.sapl");

        accumulator.registerPolicyFilePaths(filePaths);

        // Record coverage for both policies
        accumulator.recordCoverage(buildVoteWithCoverage("cthulhu-policy", Decision.PERMIT));
        accumulator.recordCoverage(buildVoteWithCoverage("dagon-policy", Decision.PERMIT));

        val coverages = accumulator.getRecord().getPolicyCoverageList();
        assertThat(coverages).hasSize(2);
        assertThat(coverages).extracting(PolicyCoverageData::getFilePath)
                .containsExactlyInAnyOrder("policies/cthulhu.sapl", "policies/dagon.sapl");
    }

    @Test
    @DisplayName("file path is null when not registered")
    void whenNoFilePathRegistered_thenFilePathIsNull() {
        val accumulator = new CoverageAccumulator("test");

        val voteWithCoverage = buildVoteWithCoverage("unknown-policy", Decision.PERMIT);
        accumulator.recordCoverage(voteWithCoverage);

        val coverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(coverage.getFilePath()).isNull();
    }

    // Helper record for test data
    private record ConditionData(int statementId, boolean result, int line) {}

    private VoteWithCoverage buildVoteWithCoverage(String policyName, Decision decision, ConditionData... conditions) {
        val hits = new ArrayList<ConditionHit>();
        for (val cond : conditions) {
            hits.add(new ConditionHit(cond.result() ? Value.TRUE : Value.FALSE, sourceLocation(cond.line()),
                    cond.statementId()));
        }

        val bodyCoverage   = new BodyCoverage(hits, conditions.length);
        val outcome        = decisionToOutcome(decision);
        val voter          = new PolicyVoterMetadata(policyName, "default", "config", null, outcome, false);
        val policyCoverage = new PolicyCoverage(voter, bodyCoverage);

        val vote = new Vote(toAuthorizationDecision(decision), List.of(), List.of(), List.of(), voter, outcome);

        return new VoteWithCoverage(vote, policyCoverage);
    }

    private Outcome decisionToOutcome(Decision decision) {
        return switch (decision) {
        case PERMIT                        -> Outcome.PERMIT;
        case DENY                          -> Outcome.DENY;
        case INDETERMINATE, NOT_APPLICABLE -> Outcome.PERMIT; // Default for test purposes
        };
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
