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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.traced.TraceFields;
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
    @DisplayName("extracts coverage from simple policy trace")
    void whenTracedPolicyWithConditions_thenExtractsCoverage() {
        val tracedDecision = buildTracedDecisionWithPolicy("elder-ritual-policy", true, new ConditionData(0, true, 3),
                new ConditionData(1, false, 5));

        val policySources = Map.of("elder-ritual-policy", CULTIST_POLICY_SOURCE);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, policySources);

        assertThat(coverages).hasSize(1);
        val coverage = coverages.getFirst();
        assertThat(coverage.getDocumentName()).isEqualTo("elder-ritual-policy");
        assertThat(coverage.getDocumentSource()).isEqualTo(CULTIST_POLICY_SOURCE);
        assertThat(coverage.getDocumentType()).isEqualTo("policy");
        assertThat(coverage.wasTargetMatched()).isTrue();
        assertThat(coverage.getConditionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("extracts coverage when target not matched")
    void whenTargetNotMatched_thenRecordsFalseHit() {
        val tracedDecision = buildTracedDecisionWithPolicy("unmatched-policy", false);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).hasSize(1);
        assertThat(coverages.getFirst().wasTargetMatched()).isFalse();
        assertThat(coverages.getFirst().wasTargetEvaluated()).isTrue();
    }

    @Test
    @DisplayName("extracts coverage from multiple policies")
    void whenMultiplePoliciesInTrace_thenExtractsAll() {
        val policy1 = buildPolicyDocument("necronomicon-policy", true, new ConditionData(0, true, 3));
        val policy2 = buildPolicyDocument("dagon-summoning", false, new ConditionData(0, false, 5));

        val tracedDecision = buildTracedDecisionWithDocuments(policy1, policy2);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).hasSize(2);
        assertThat(coverages).extracting("documentName").containsExactlyInAnyOrder("necronomicon-policy",
                "dagon-summoning");
    }

    @Test
    @DisplayName("handles policy set type correctly")
    void whenPolicySetType_thenDocumentTypeIsSet() {
        val document = buildDocumentObject("miskatonic-rules", TraceFields.TYPE_SET, true);

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).hasSize(1);
        assertThat(coverages.getFirst().getDocumentType()).isEqualTo("set");
    }

    @Test
    @DisplayName("returns empty list for empty documents array")
    void whenNoDocuments_thenReturnsEmptyList() {
        val tracedDecision = buildTracedDecisionWithDocuments();

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).isEmpty();
    }

    @Test
    @DisplayName("handles missing name field gracefully")
    void whenDocumentMissingName_thenSkipsDocument() {
        val invalidDocument = ObjectValue.builder().put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                .put(TraceFields.TARGET_RESULT, Value.of(true)).build();

        val tracedDecision = buildTracedDecisionWithDocuments(invalidDocument);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).isEmpty();
    }

    @Test
    @DisplayName("handles non-object document gracefully")
    void whenDocumentIsNotObject_thenSkipsDocument() {
        val trace          = ObjectValue.builder()
                .put(TraceFields.DOCUMENTS, ArrayValue.builder().add(Value.of("not an object")).build()).build();
        val tracedDecision = ObjectValue.builder().put(TraceFields.TRACE, trace).build();

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages).isEmpty();
    }

    @Test
    @DisplayName("extracts condition hits with correct branch data")
    void whenConditionsPresent_thenExtractsBranchHits() {
        val tracedDecision = buildTracedDecisionWithPolicy("arkham-access", true, new ConditionData(0, true, 3),
                new ConditionData(0, false, 3), new ConditionData(1, true, 5));

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        val coverage = coverages.getFirst();
        assertThat(coverage.getConditionCount()).isEqualTo(2);
        assertThat(coverage.getFullyCoveredConditionCount()).isOne();
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("skips condition with missing statementId")
    void whenConditionMissingStatementId_thenSkipsCondition() {
        val condition = ObjectValue.builder().put(TraceFields.RESULT, Value.of(true))
                .put(TraceFields.START_LINE, Value.of(3)).build();

        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of("incomplete-policy"))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY)).put(TraceFields.TARGET_RESULT, Value.of(true))
                .put(TraceFields.CONDITIONS, ArrayValue.builder().add(condition).build()).build();

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages.getFirst().getConditionCount()).isZero();
    }

    @Test
    @DisplayName("skips condition with missing result")
    void whenConditionMissingResult_thenSkipsCondition() {
        val condition = ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(0))
                .put(TraceFields.START_LINE, Value.of(3)).build();

        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of("incomplete-policy"))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY)).put(TraceFields.TARGET_RESULT, Value.of(true))
                .put(TraceFields.CONDITIONS, ArrayValue.builder().add(condition).build()).build();

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages.getFirst().getConditionCount()).isZero();
    }

    @Test
    @DisplayName("uses default line zero when line missing")
    void whenConditionMissingLine_thenUsesZero() {
        val condition = ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(0))
                .put(TraceFields.RESULT, Value.of(true)).build();

        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of("lineless-policy"))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY)).put(TraceFields.TARGET_RESULT, Value.of(true))
                .put(TraceFields.CONDITIONS, ArrayValue.builder().add(condition).build()).build();

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        val branchHits = coverages.getFirst().getBranchHits();
        assertThat(branchHits).hasSize(1);
        assertThat(branchHits.getFirst().line()).isZero();
    }

    @Test
    @DisplayName("hasCoverageData returns true when conditions present")
    void whenConditionsPresent_thenHasCoverageDataReturnsTrue() {
        val tracedDecision = buildTracedDecisionWithPolicy("policy", true, new ConditionData(0, true, 3));

        assertThat(CoverageExtractor.hasCoverageData(tracedDecision)).isTrue();
    }

    @Test
    @DisplayName("hasCoverageData returns true when targetResult present")
    void whenTargetResultPresent_thenHasCoverageDataReturnsTrue() {
        val tracedDecision = buildTracedDecisionWithPolicy("policy", false);

        assertThat(CoverageExtractor.hasCoverageData(tracedDecision)).isTrue();
    }

    @Test
    @DisplayName("hasCoverageData returns false when no coverage fields")
    void whenNoCoverageFields_thenHasCoverageDataReturnsFalse() {
        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of("no-coverage-policy"))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY)).build();

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        assertThat(CoverageExtractor.hasCoverageData(tracedDecision)).isFalse();
    }

    @Test
    @DisplayName("hasCoverageData returns false for empty trace")
    void whenEmptyTrace_thenHasCoverageDataReturnsFalse() {
        val tracedDecision = buildTracedDecisionWithDocuments();

        assertThat(CoverageExtractor.hasCoverageData(tracedDecision)).isFalse();
    }

    @Test
    @DisplayName("uses targetMatch field when targetResult absent")
    void whenTargetMatchPresentButNotTargetResult_thenUsesTargetMatch() {
        val document = ObjectValue.builder().put(TraceFields.NAME, Value.of("alternate-target-policy"))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY)).put(TraceFields.TARGET_MATCH, Value.of(true))
                .build();

        val tracedDecision = buildTracedDecisionWithDocuments(document);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, Map.of());

        assertThat(coverages.getFirst().wasTargetMatched()).isTrue();
    }

    @Test
    @DisplayName("resolves policy source from provided map")
    void whenPolicySourceProvided_thenAttachesToCoverage() {
        val tracedDecision = buildTracedDecisionWithPolicy("sourced-policy", true);
        val sourceCode     = "policy \"sourced-policy\" permit";
        val policySources  = Map.of("sourced-policy", sourceCode);

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, policySources);

        assertThat(coverages.getFirst().getDocumentSource()).isEqualTo(sourceCode);
    }

    @Test
    @DisplayName("uses empty source when policy not in map")
    void whenPolicySourceNotProvided_thenUsesEmptyString() {
        val tracedDecision = buildTracedDecisionWithPolicy("unknown-policy", true);
        val policySources  = new HashMap<String, String>();

        val coverages = CoverageExtractor.extractCoverage(tracedDecision, policySources);

        assertThat(coverages.getFirst().getDocumentSource()).isEmpty();
    }

    // Helper record for test data
    private record ConditionData(int statementId, boolean result, int line) {}

    private Value buildTracedDecisionWithPolicy(String policyName, boolean targetResult, ConditionData... conditions) {
        val document = buildPolicyDocument(policyName, targetResult, conditions);
        return buildTracedDecisionWithDocuments(document);
    }

    private Value buildPolicyDocument(String name, boolean targetResult, ConditionData... conditions) {
        return buildDocumentObject(name, TraceFields.TYPE_POLICY, targetResult, conditions);
    }

    private Value buildDocumentObject(String name, String type, boolean targetResult, ConditionData... conditions) {
        val conditionsArray = ArrayValue.builder();
        for (val condition : conditions) {
            // Use the new position fields (startLine, endLine, startChar, endChar)
            conditionsArray.add(ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(condition.statementId()))
                    .put(TraceFields.RESULT, Value.of(condition.result()))
                    .put(TraceFields.START_LINE, Value.of(condition.line()))
                    .put(TraceFields.END_LINE, Value.of(condition.line())).put(TraceFields.START_CHAR, Value.of(0))
                    .put(TraceFields.END_CHAR, Value.of(0)).build());
        }

        return ObjectValue.builder().put(TraceFields.NAME, Value.of(name)).put(TraceFields.TYPE, Value.of(type))
                .put(TraceFields.TARGET_RESULT, Value.of(targetResult))
                .put(TraceFields.CONDITIONS, conditionsArray.build()).build();
    }

    private Value buildTracedDecisionWithDocuments(Value... documents) {
        val docsArray = ArrayValue.builder();
        for (val doc : documents) {
            docsArray.add(doc);
        }

        val trace = ObjectValue.builder().put(TraceFields.DOCUMENTS, docsArray.build()).build();

        return ObjectValue.builder().put(TraceFields.TRACE, trace).build();
    }
}
