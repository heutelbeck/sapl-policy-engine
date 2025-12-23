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

import io.sapl.api.coverage.PolicyCoverageData;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TraceFields;
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
    @DisplayName("records coverage from traced decision value")
    void whenRecordCoverageFromValue_thenCoverageAccumulated() {
        val accumulator = new CoverageAccumulator("test");

        val trace    = buildTracedDecision("elder-ritual", true, new ConditionData(0, true, 3));
        val decision = AuthorizationDecision.PERMIT;

        accumulator.recordCoverage(trace, decision);

        assertThat(accumulator.hasCoverage()).isTrue();
        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getEvaluationCount()).isOne();
        assertThat(coverageRecord.getPolicyCount()).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.PERMIT)).isOne();
    }

    @Test
    @DisplayName("records coverage from trace with policy source correlation")
    void whenRecordCoverageWithSource_thenSourceAttached() {
        val accumulator = new CoverageAccumulator("test");
        val source      = "policy \"summoning\" permit where resource.type == \"scroll\";";

        accumulator.registerPolicySource("summoning", source);

        val trace    = buildTracedDecision("summoning", true, new ConditionData(0, false, 5));
        val decision = AuthorizationDecision.DENY;

        accumulator.recordCoverageFromTrace(decision, trace);

        val policyCoverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(policyCoverage.getDocumentSource()).isEqualTo(source);
    }

    @Test
    @DisplayName("accumulates multiple evaluations")
    void whenMultipleEvaluations_thenAllAccumulated() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.recordCoverage(buildTracedDecision("policy1", true), AuthorizationDecision.PERMIT);
        accumulator.recordCoverage(buildTracedDecision("policy2", false), AuthorizationDecision.DENY);
        accumulator.recordCoverage(buildTracedDecision("policy1", true, new ConditionData(0, true, 3)),
                AuthorizationDecision.PERMIT);

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
        accumulator.recordCoverage(buildTracedDecision("shared-policy", true, new ConditionData(0, true, 3)),
                AuthorizationDecision.PERMIT);

        // Second evaluation: false branch
        accumulator.recordCoverage(buildTracedDecision("shared-policy", true, new ConditionData(0, false, 3)),
                AuthorizationDecision.PERMIT);

        val coverageRecord = accumulator.getRecord();
        val coverage       = coverageRecord.getPolicyCoverageList().getFirst();

        assertThat(coverageRecord.getPolicyCount()).isOne();
        assertThat(coverage.getFullyCoveredConditionCount()).isOne();
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(100.0);
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

        accumulator.recordCoverage(buildTracedDecision("policy1", true, new ConditionData(0, true, 3)),
                AuthorizationDecision.PERMIT);

        val summary = accumulator.getSummary();

        assertThat(summary).contains("arkham-test").contains("policies=1").contains("evaluations=1")
                .contains("branchCoverage=");
    }

    @Test
    @DisplayName("handles empty trace gracefully")
    void whenEmptyTrace_thenNoExceptionAndNoCoverage() {
        val accumulator = new CoverageAccumulator("test");
        val emptyTrace  = buildEmptyTrace();

        accumulator.recordCoverage(emptyTrace, AuthorizationDecision.NOT_APPLICABLE);

        val coverageRecord = accumulator.getRecord();
        assertThat(coverageRecord.getEvaluationCount()).isOne();
        assertThat(coverageRecord.getPolicyCount()).isZero();
        assertThat(coverageRecord.getDecisionCount(Decision.NOT_APPLICABLE)).isOne();
    }

    @Test
    @DisplayName("handles all decision types")
    void whenVariousDecisions_thenAllCounted() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.recordCoverage(buildEmptyTrace(), AuthorizationDecision.PERMIT);
        accumulator.recordCoverage(buildEmptyTrace(), AuthorizationDecision.DENY);
        accumulator.recordCoverage(buildEmptyTrace(), AuthorizationDecision.INDETERMINATE);
        accumulator.recordCoverage(buildEmptyTrace(), AuthorizationDecision.NOT_APPLICABLE);

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

        val trace    = buildTracedDecision("elder-policy", true);
        val decision = AuthorizationDecision.PERMIT;
        accumulator.recordCoverage(trace, decision);

        val coverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(coverage.getFilePath()).isEqualTo("policies/elder-access.sapl");
    }

    @Test
    @DisplayName("registers multiple policy file paths")
    void whenRegisterPolicyFilePaths_thenAllFilePathsSetOnCoverage() {
        val accumulator = new CoverageAccumulator("test");
        val filePaths   = Map.of("cthulhu-policy", "policies/cthulhu.sapl", "dagon-policy", "policies/dagon.sapl");

        accumulator.registerPolicyFilePaths(filePaths);

        accumulator.recordCoverage(buildTracedDecisionWithMultiplePolicies("cthulhu-policy", "dagon-policy"),
                AuthorizationDecision.PERMIT);

        val coverages = accumulator.getRecord().getPolicyCoverageList();
        assertThat(coverages).hasSize(2);
        assertThat(coverages).extracting(PolicyCoverageData::getFilePath)
                .containsExactlyInAnyOrder("policies/cthulhu.sapl", "policies/dagon.sapl");
    }

    @Test
    @DisplayName("file path is null when not registered")
    void whenNoFilePathRegistered_thenFilePathIsNull() {
        val accumulator = new CoverageAccumulator("test");

        val trace    = buildTracedDecision("unknown-policy", true);
        val decision = AuthorizationDecision.PERMIT;
        accumulator.recordCoverage(trace, decision);

        val coverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(coverage.getFilePath()).isNull();
    }

    @Test
    @DisplayName("file path set via recordCoverageFromTrace")
    void whenRecordCoverageFromTrace_thenFilePathSetOnCoverage() {
        val accumulator = new CoverageAccumulator("test");

        accumulator.registerPolicyFilePath("trace-policy", "resources/trace-policy.sapl");

        val trace    = buildTracedDecision("trace-policy", true);
        val decision = AuthorizationDecision.PERMIT;
        accumulator.recordCoverageFromTrace(decision, trace);

        val coverage = accumulator.getRecord().getPolicyCoverageList().getFirst();
        assertThat(coverage.getFilePath()).isEqualTo("resources/trace-policy.sapl");
    }

    // Helper record for test data
    private record ConditionData(int statementId, boolean result, int line) {}

    private Value buildTracedDecision(String policyName, boolean targetResult, ConditionData... conditions) {
        val conditionsArray = ArrayValue.builder();
        for (val condition : conditions) {
            conditionsArray.add(ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(condition.statementId()))
                    .put(TraceFields.RESULT, Value.of(condition.result()))
                    .put(TraceFields.LINE, Value.of(condition.line())).build());
        }

        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of(policyName))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                .put(TraceFields.TARGET_RESULT, Value.of(targetResult))
                .put(TraceFields.CONDITIONS, conditionsArray.build()).build();

        val trace = ObjectValue.builder().put(TraceFields.DOCUMENTS, ArrayValue.builder().add(document).build())
                .build();

        return ObjectValue.builder().put(TraceFields.TRACE, trace).build();
    }

    private Value buildEmptyTrace() {
        val trace = ObjectValue.builder().put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY).build();

        return ObjectValue.builder().put(TraceFields.TRACE, trace).build();
    }

    private Value buildTracedDecisionWithMultiplePolicies(String... policyNames) {
        val documents = ArrayValue.builder();
        for (val name : policyNames) {
            val document = ObjectValue.builder().put(TraceFields.NAME, Value.of(name))
                    .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                    .put(TraceFields.TARGET_RESULT, Value.of(true)).put(TraceFields.CONDITIONS, Value.EMPTY_ARRAY)
                    .build();
            documents.add(document);
        }

        val trace = ObjectValue.builder().put(TraceFields.DOCUMENTS, documents.build()).build();

        return ObjectValue.builder().put(TraceFields.TRACE, trace).build();
    }
}
