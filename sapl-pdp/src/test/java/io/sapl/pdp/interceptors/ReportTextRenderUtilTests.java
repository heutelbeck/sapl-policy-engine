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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportTextRenderUtil")
class ReportTextRenderUtilTests {

    private static final String                    DUMMY_TIMESTAMP       = "2026-01-01T00:00:00Z";
    private static final String                    DUMMY_SUBSCRIPTION_ID = "sub-456";
    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION    = AuthorizationSubscription.of("testUser",
            "read", "testResource");
    private static final CombiningAlgorithm        DENY_OVERRIDES        = new CombiningAlgorithm(
            VotingMode.PRIORITY_DENY, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    @Test
    @DisplayName("renders decision in text report")
    void whenTextReportThenContainsDecision() {
        val report = createSimpleReport(Decision.PERMIT);

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Decision       :").contains("PERMIT");
    }

    @Test
    @DisplayName("renders PDP ID in text report")
    void whenTextReportThenContainsPdpId() {
        val report = createSimpleReport(Decision.PERMIT);

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("PDP ID         : cthulhu-pdp");
    }

    @Test
    @DisplayName("renders algorithm in text report")
    void whenTextReportThenContainsAlgorithm() {
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "test-pdp", "test-config", DENY_OVERRIDES,
                List.of(), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Algorithm      :").contains("PRIORITY_DENY");
    }

    @Test
    @DisplayName("renders PDP-level errors in text report")
    void whenReportHasPdpErrorsThenErrorsAreRendered() {
        val error  = new ErrorValue("Ritual interrupted by investigators", null);
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.INDETERMINATE,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "test-pdp", "test-config", DENY_OVERRIDES,
                List.of(), List.of(error));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("PDP Errors:").contains("Ritual interrupted by investigators");
    }

    @Test
    @DisplayName("renders contributing documents in text report")
    void whenReportHasDocumentsThenDocumentsAreRendered() {
        val doc    = new ContributingDocument("forbidden-knowledge-access", Decision.PERMIT, List.of(), List.of());
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "test-pdp", "test-config", DENY_OVERRIDES,
                List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Documents:").contains("forbidden-knowledge-access -> PERMIT");
    }

    @Test
    @DisplayName("renders multiple contributing documents")
    void whenReportHasMultipleDocumentsThenAllAreRendered() {
        val doc1   = new ContributingDocument("outer-set", Decision.DENY, List.of(), List.of());
        val doc2   = new ContributingDocument("inner-policy", Decision.DENY, List.of(), List.of());
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.DENY,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "top-set", "test-pdp", "test-config", DENY_OVERRIDES,
                List.of(doc1, doc2), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("outer-set -> DENY").contains("inner-policy -> DENY");
    }

    @Test
    @DisplayName("renders obligations when present")
    void whenObligationsPresentThenObligationsAreRendered() {
        val obligations = ArrayValue.builder().add(Value.of("log_access")).build();
        val report      = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                obligations, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES,
                List.of(), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Obligations:").contains("log_access");
    }

    @Test
    @DisplayName("renders advice when present")
    void whenAdvicePresentThenAdviceIsRendered() {
        val advice = ArrayValue.builder().add(Value.of("consider_logging")).build();
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                Value.EMPTY_ARRAY, advice, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(),
                List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Advice:").contains("consider_logging");
    }

    @Test
    @DisplayName("renders environment attributes with timestamp under contributing document")
    void whenDocumentHasEnvironmentAttributesThenAttributesAndTimestampAreRendered() {
        val timestamp  = Instant.parse("2024-01-23T10:30:05.123Z");
        val invocation = new AttributeFinderInvocation("test-config", "time.now", List.of(), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false, EMPTY_CTX);
        val attr       = new AttributeRecord(invocation, Value.of("2024-01-23T10:30:00Z"), timestamp, null);
        val doc        = new ContributingDocument("time-policy", Decision.PERMIT, List.of(attr), List.of());
        val report     = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES,
                List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("time-policy -> PERMIT").contains("Attributes:").contains("<time.now> = ")
                .contains("2024-01-23T10:30:00Z").contains("@ 2024-01-23T10:30:05.123Z");
    }

    @Test
    @DisplayName("renders entity attributes with timestamp under contributing document")
    void whenDocumentHasEntityAttributesThenAttributesAndTimestampAreRendered() {
        val timestamp  = Instant.parse("2024-01-23T10:30:05.456Z");
        val entity     = Value.of("test-subject");
        val invocation = new AttributeFinderInvocation("test-config", "user.role", entity, List.of(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false, EMPTY_CTX);
        val attr       = new AttributeRecord(invocation, Value.of("admin"), timestamp, null);
        val doc        = new ContributingDocument("role-policy", Decision.PERMIT, List.of(attr), List.of());
        val report     = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.PERMIT,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES,
                List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("role-policy -> PERMIT").contains("Attributes:")
                .contains("\"test-subject\".<user.role> = ").contains("\"admin\"")
                .contains("@ 2024-01-23T10:30:05.456Z");
    }

    @Test
    @DisplayName("renders document-level errors under contributing document")
    void whenDocumentHasErrorsThenErrorsAreRendered() {
        val error  = new ErrorValue("Failed to evaluate condition", null);
        val doc    = new ContributingDocument("broken-policy", Decision.INDETERMINATE, List.of(), List.of(error));
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.INDETERMINATE,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES,
                List.of(doc), List.of());

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("broken-policy -> INDETERMINATE").contains("Errors:")
                .contains("Failed to evaluate condition");
    }

    private VoteReport createSimpleReport(Decision decision) {
        return new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, decision, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", null, List.of(), List.of());
    }
}
