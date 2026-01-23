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
package io.sapl.pdp.interceptors;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportTextRenderUtil")
class ReportTextRenderUtilTests {

    private static final CombiningAlgorithm DENY_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    @Test
    @DisplayName("renders decision in text report")
    void whenTextReport_thenContainsDecision() {
        val report = createSimpleReport(Decision.PERMIT);

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Decision :").contains("PERMIT");
    }

    @Test
    @DisplayName("renders PDP ID in text report")
    void whenTextReport_thenContainsPdpId() {
        val report = createSimpleReport(Decision.PERMIT);

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("PDP ID   : cthulhu-pdp");
    }

    @Test
    @DisplayName("renders algorithm in text report")
    void whenTextReport_thenContainsAlgorithm() {
        val report = new VoteReport(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "test-pdp",
                "test-config", DENY_OVERRIDES, List.of(), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Algorithm:").contains("PRIORITY_DENY");
    }

    @Test
    @DisplayName("renders PDP-level errors in text report")
    void whenReportHasPdpErrors_thenErrorsAreRendered() {
        val error  = new ErrorValue("Ritual interrupted by investigators", null);
        val report = new VoteReport(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set",
                "test-pdp", "test-config", DENY_OVERRIDES, List.of(), List.of(error));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("PDP Errors:").contains("Ritual interrupted by investigators");
    }

    @Test
    @DisplayName("renders contributing documents in text report")
    void whenReportHasDocuments_thenDocumentsAreRendered() {
        val doc    = new ContributingDocument("forbidden-knowledge-access", Decision.PERMIT, List.of(), List.of());
        val report = new VoteReport(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "test-pdp",
                "test-config", DENY_OVERRIDES, List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Documents:").contains("forbidden-knowledge-access -> PERMIT");
    }

    @Test
    @DisplayName("renders multiple contributing documents")
    void whenReportHasMultipleDocuments_thenAllAreRendered() {
        val doc1   = new ContributingDocument("outer-set", Decision.DENY, List.of(), List.of());
        val doc2   = new ContributingDocument("inner-policy", Decision.DENY, List.of(), List.of());
        val report = new VoteReport(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "top-set", "test-pdp",
                "test-config", DENY_OVERRIDES, List.of(doc1, doc2), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("outer-set -> DENY").contains("inner-policy -> DENY");
    }

    @Test
    @DisplayName("renders obligations when present")
    void whenObligationsPresent_thenObligationsAreRendered() {
        val obligations = ArrayValue.builder().add(Value.of("log_access")).build();
        val report      = new VoteReport(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, null, "test-set",
                "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Obligations:").contains("log_access");
    }

    @Test
    @DisplayName("renders advice when present")
    void whenAdvicePresent_thenAdviceIsRendered() {
        val advice = ArrayValue.builder().add(Value.of("consider_logging")).build();
        val report = new VoteReport(Decision.PERMIT, Value.EMPTY_ARRAY, advice, null, "test-set", "cthulhu-pdp",
                "test-config", DENY_OVERRIDES, List.of(), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Advice:").contains("consider_logging");
    }

    @Test
    @DisplayName("renders environment attributes with timestamp under contributing document")
    void whenDocumentHasEnvironmentAttributes_thenAttributesAndTimestampAreRendered() {
        val timestamp  = Instant.parse("2024-01-23T10:30:05.123Z");
        val invocation = new AttributeFinderInvocation("test-config", "time.now", List.of(), Map.of(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false);
        val attr       = new AttributeRecord(invocation, Value.of("2024-01-23T10:30:00Z"), timestamp, null);
        val doc        = new ContributingDocument("time-policy", Decision.PERMIT, List.of(attr), List.of());
        val report     = new VoteReport(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set",
                "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("time-policy -> PERMIT").contains("Attributes:").contains("<time.now> = ")
                .contains("2024-01-23T10:30:00Z").contains("@ 2024-01-23T10:30:05.123Z");
    }

    @Test
    @DisplayName("renders entity attributes with timestamp under contributing document")
    void whenDocumentHasEntityAttributes_thenAttributesAndTimestampAreRendered() {
        val timestamp  = Instant.parse("2024-01-23T10:30:05.456Z");
        val entity     = Value.of("test-subject");
        val invocation = new AttributeFinderInvocation("test-config", "user.role", entity, List.of(), Map.of(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false);
        val attr       = new AttributeRecord(invocation, Value.of("admin"), timestamp, null);
        val doc        = new ContributingDocument("role-policy", Decision.PERMIT, List.of(attr), List.of());
        val report     = new VoteReport(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set",
                "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("role-policy -> PERMIT").contains("Attributes:")
                .contains("\"test-subject\".<user.role> = ").contains("\"admin\"")
                .contains("@ 2024-01-23T10:30:05.456Z");
    }

    @Test
    @DisplayName("renders document-level errors under contributing document")
    void whenDocumentHasErrors_thenErrorsAreRendered() {
        val error  = new ErrorValue("Failed to evaluate condition", null);
        val doc    = new ContributingDocument("broken-policy", Decision.INDETERMINATE, List.of(), List.of(error));
        val report = new VoteReport(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set",
                "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("broken-policy -> INDETERMINATE").contains("Errors:")
                .contains("Failed to evaluate condition");
    }

    private VoteReport createSimpleReport(Decision decision) {
        return new VoteReport(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp",
                "test-config", null, List.of(), List.of());
    }
}
