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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.document.AttributeContribution;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportTextRenderUtil")
class ReportTextRenderUtilTests {

    private static final Instant                   DUMMY_TIMESTAMP       = Instant.parse("2026-01-01T00:00:00Z");
    private static final String                    DUMMY_SUBSCRIPTION_ID = "sub-456";
    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION    = AuthorizationSubscription.of("testUser",
            "read", "testResource");
    private static final CombiningAlgorithm        DENY_OVERRIDES        = new CombiningAlgorithm(
            VotingMode.PRIORITY_DENY, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);
    private static final AttributeAccessContext    EMPTY_CTX             = new AttributeAccessContext(
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "Decision       : PERMIT", "PDP ID         : cthulhu-pdp",
            "Algorithm      : PRIORITY_DENY" })
    @DisplayName("text report contains the expected header line")
    void whenTextReportThenContainsExpectedHeaderLine(String expectedLine) {
        val report = reportWithDecision(Decision.PERMIT);

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains(expectedLine);
    }

    @Test
    @DisplayName("renders PDP-level errors in text report")
    void whenReportHasPdpErrorsThenErrorsAreRendered() {
        val error  = new ErrorValue("Ritual interrupted by investigators", null);
        val report = new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, Decision.INDETERMINATE,
                Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES,
                List.of(), List.of(error));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("PDP Errors:").contains("- Ritual interrupted by investigators");
    }

    @Test
    @DisplayName("renders contributing documents in text report")
    void whenReportHasDocumentsThenDocumentsAreRendered() {
        val doc    = new ContributingDocument("forbidden-knowledge-access", Decision.PERMIT, List.of(), List.of());
        val report = reportWithDocuments(Decision.PERMIT, List.of(doc));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("Documents:").contains("forbidden-knowledge-access -> PERMIT");
    }

    @Test
    @DisplayName("renders multiple contributing documents in evaluation order")
    void whenReportHasMultipleDocumentsThenAllAreRenderedInOrder() {
        val doc1   = new ContributingDocument("outer-set", Decision.DENY, List.of(), List.of());
        val doc2   = new ContributingDocument("inner-policy", Decision.DENY, List.of(), List.of());
        val report = reportWithDocuments(Decision.DENY, List.of(doc1, doc2));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("outer-set -> DENY").contains("inner-policy -> DENY");
        assertThat(text.indexOf("outer-set -> DENY")).isLessThan(text.indexOf("inner-policy -> DENY"));
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
    @DisplayName("renders environment attribute as <name> under its document with value and publish timestamp")
    void whenDocumentHasEnvironmentAttributeThenItIsRenderedUnderThatDocument() {
        val publishedAt  = Instant.parse("2024-01-23T10:30:05.123Z");
        val contribution = environmentAttribute("time.now", Value.of("2024-01-23T10:30:00Z"), publishedAt,
                "time-policy");
        val doc          = new ContributingDocument("time-policy", Decision.PERMIT, List.of(), List.of(contribution));
        val report       = reportWithDocuments(Decision.PERMIT, List.of(doc));

        val text = ReportTextRenderUtil.textReport(report);

        val expectedLine = "<time.now> = \"2024-01-23T10:30:00Z\" @ "
                + ReportTextRenderUtil.formatTimestamp(publishedAt);
        assertThat(text).contains("time-policy -> PERMIT").contains("Attributes:").contains(expectedLine);
        assertThat(text.indexOf("time-policy -> PERMIT")).isLessThan(text.indexOf("Attributes:"));
        assertThat(text.indexOf("Attributes:")).isLessThan(text.indexOf(expectedLine));
    }

    @Test
    @DisplayName("renders entity attribute as <entity>.<name> under its document with value and publish timestamp")
    void whenDocumentHasEntityAttributeThenItIsRenderedUnderThatDocument() {
        val publishedAt  = Instant.parse("2024-01-23T10:30:05.456Z");
        val contribution = entityAttribute(Value.of("test-subject"), "user.role", Value.of("admin"), publishedAt,
                "role-policy");
        val doc          = new ContributingDocument("role-policy", Decision.PERMIT, List.of(), List.of(contribution));
        val report       = reportWithDocuments(Decision.PERMIT, List.of(doc));

        val text = ReportTextRenderUtil.textReport(report);

        val expectedLine = "\"test-subject\".<user.role> = \"admin\" @ "
                + ReportTextRenderUtil.formatTimestamp(publishedAt);
        assertThat(text).contains("role-policy -> PERMIT").contains("Attributes:").contains(expectedLine);
        assertThat(text.indexOf("role-policy -> PERMIT")).isLessThan(text.indexOf("Attributes:"));
        assertThat(text.indexOf("Attributes:")).isLessThan(text.indexOf(expectedLine));
    }

    @Test
    @DisplayName("attribute reads of one document do not leak into a sibling document's section")
    void whenTwoDocumentsHaveDifferentAttributesThenTheyAreSegregated() {
        val publishedAt = Instant.parse("2024-01-23T10:30:05.123Z");
        val timeAttr    = environmentAttribute("time.now", Value.of("2024-01-23T10:30:00Z"), publishedAt, "policy-A");
        val roleAttr    = entityAttribute(Value.of("test-subject"), "user.role", Value.of("admin"), publishedAt,
                "policy-B");
        val docA        = new ContributingDocument("policy-A", Decision.PERMIT, List.of(), List.of(timeAttr));
        val docB        = new ContributingDocument("policy-B", Decision.PERMIT, List.of(), List.of(roleAttr));
        val report      = reportWithDocuments(Decision.PERMIT, List.of(docA, docB));

        val text = ReportTextRenderUtil.textReport(report);

        val timeNowIndex  = text.indexOf("<time.now>");
        val userRoleIndex = text.indexOf("<user.role>");
        val policyAIndex  = text.indexOf("policy-A -> PERMIT");
        val policyBIndex  = text.indexOf("policy-B -> PERMIT");
        assertThat(timeNowIndex).isGreaterThan(policyAIndex).isLessThan(policyBIndex);
        assertThat(userRoleIndex).isGreaterThan(policyBIndex);
    }

    @Test
    @DisplayName("renders multiple attributes for one document, each on its own line")
    void whenDocumentHasMultipleAttributesThenAllAreRendered() {
        val publishedAt = Instant.parse("2024-01-23T10:30:05.000Z");
        val timeAttr    = environmentAttribute("time.now", Value.of("2024-01-23T10:30:00Z"), publishedAt, "multi-attr");
        val roleAttr    = entityAttribute(Value.of("user-1"), "pip.role", Value.of("admin"), publishedAt, "multi-attr");
        val doc         = new ContributingDocument("multi-attr", Decision.PERMIT, List.of(),
                List.of(timeAttr, roleAttr));
        val report      = reportWithDocuments(Decision.PERMIT, List.of(doc));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("<time.now> = \"2024-01-23T10:30:00Z\"")
                .contains("\"user-1\".<pip.role> = \"admin\"");
    }

    @Test
    @DisplayName("renders document-level errors under contributing document")
    void whenDocumentHasErrorsThenErrorsAreRendered() {
        val error  = new ErrorValue("Failed to evaluate condition", null);
        val doc    = new ContributingDocument("broken-policy", Decision.INDETERMINATE, List.of(error), List.of());
        val report = reportWithDocuments(Decision.INDETERMINATE, List.of(doc));

        val text = ReportTextRenderUtil.textReport(report);

        assertThat(text).contains("broken-policy -> INDETERMINATE").contains("Errors:")
                .contains("- Failed to evaluate condition");
    }

    private VoteReport reportWithDecision(Decision decision) {
        return new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, decision, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES, List.of(),
                List.of());
    }

    private VoteReport reportWithDocuments(Decision decision, List<ContributingDocument> documents) {
        return new VoteReport(DUMMY_TIMESTAMP, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION, decision, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, null, "test-set", "cthulhu-pdp", "test-config", DENY_OVERRIDES, documents,
                List.of());
    }

    private static AttributeContribution environmentAttribute(String attributeName, Value value, Instant publishedAt,
            String documentName) {
        val invocation = new AttributeFinderInvocation("test-config", attributeName, List.of(), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false, EMPTY_CTX);
        val key        = new SubscriptionKey(invocation, false);
        return new AttributeContribution(key, value, publishedAt, List.of(occurrence(documentName)));
    }

    private static AttributeContribution entityAttribute(Value entity, String attributeName, Value value,
            Instant publishedAt, String documentName) {
        val invocation = new AttributeFinderInvocation("test-config", attributeName, entity, List.of(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false, EMPTY_CTX);
        val key        = new SubscriptionKey(invocation, false);
        return new AttributeContribution(key, value, publishedAt, List.of(occurrence(documentName)));
    }

    private static Occurrence occurrence(String documentName) {
        return new Occurrence(new SourceLocation(documentName, null, 0, 0, 1, 1, 1, 1));
    }
}
