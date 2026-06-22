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
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportBuilderUtil")
class ReportBuilderUtilTests {

    private static final Instant                   DUMMY_TIMESTAMP       = Instant.parse("2026-01-01T00:00:00Z");
    private static final String                    DUMMY_SUBSCRIPTION_ID = "sub-789";
    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION    = AuthorizationSubscription.of("testUser",
            "read", "testResource");
    private static final CombiningAlgorithm        DENY_OVERRIDES        = new CombiningAlgorithm(
            VotingMode.PRIORITY_DENY, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);
    private static final AttributeAccessContext    EMPTY_CTX             = new AttributeAccessContext(
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    @Test
    @DisplayName("extracts decision from vote")
    void whenExtractReportThenDecisionIsExtracted() {
        val tracedVote = traced(createSimplePermitVote());

        val report = ReportBuilderUtil.extractReport(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.decision()).isEqualTo(Decision.PERMIT);
    }

    @Test
    @DisplayName("extracts PDP metadata from vote")
    void whenExtractReportThenPdpMetadataIsExtracted() {
        val tracedVote = traced(createSimplePermitVote());

        val report = ReportBuilderUtil.extractReport(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report).satisfies(r -> {
            assertThat(r.pdpId()).isEqualTo("cthulhu-pdp");
            assertThat(r.configurationId()).isEqualTo("test-security");
        });
    }

    @Test
    @DisplayName("extracts obligations from vote")
    void whenExtractReportThenObligationsAreExtracted() {
        val obligation    = Value.of("log_access");
        val obligations   = ArrayValue.builder().add(obligation).build();
        val authzDecision = new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
        val voter         = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, true);
        val vote          = new Vote(authzDecision, List.of(), List.of(), voter, Outcome.PERMIT);

        val report = ReportBuilderUtil.extractReport(traced(vote), DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.obligations()).contains(obligation);
    }

    @Test
    @DisplayName("extracts contributing documents from nested votes")
    void whenVoteHasContributingVotesThenContributingDocumentsAreExtracted() {
        val policyVoter = new PolicyVoterMetadata("forbidden-knowledge-access", "cthulhu-pdp", "test-security", "doc-1",
                Outcome.PERMIT, false);
        val policyVote  = Vote.of(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, policyVoter);

        val setVoter = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        val vote     = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote), Outcome.PERMIT);

        val report = ReportBuilderUtil.extractReport(traced(vote), DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.contributingDocuments()).singleElement().satisfies(doc -> {
            assertThat(doc.name()).isEqualTo("test-set->forbidden-knowledge-access");
            assertThat(doc.decision()).isEqualTo(Decision.PERMIT);
            assertThat(doc.attributes()).isEmpty();
        });
    }

    @Test
    @DisplayName("handles vote with no contributing votes")
    void whenVoteHasNoContributingVotesThenReportHasEmptyContributingDocuments() {
        val voter = new PolicySetVoterMetadata("minimal-set", "minimal-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.DENY, false);
        val vote  = new Vote(AuthorizationDecision.INDETERMINATE, List.of(), List.of(), voter, Outcome.DENY);

        val report = ReportBuilderUtil.extractReport(traced(vote), DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report).satisfies(r -> {
            assertThat(r.decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(r.contributingDocuments()).isEmpty();
        });
    }

    @Test
    @DisplayName("converts report to ObjectValue with decision and pdpId fields")
    void whenToObjectValueThenReportIsConvertedToObjectValue() {
        val report = ReportBuilderUtil.extractReport(traced(createSimplePermitVote()), DUMMY_SUBSCRIPTION_ID,
                DUMMY_SUBSCRIPTION);

        val objectValue = ReportBuilderUtil.toObjectValue(report);

        assertThat(objectValue).satisfies(v -> {
            assertThat(v.get("decision")).isEqualTo(Value.of("PERMIT"));
            assertThat(v.get("pdpId")).isEqualTo(Value.of("cthulhu-pdp"));
        });
    }

    @Test
    @DisplayName("extractReportAsValue combines extraction and JSON conversion in one call")
    void whenExtractReportAsValueThenReturnsObjectValue() {
        val tracedVote = traced(createSimplePermitVote());

        val objectValue = ReportBuilderUtil.extractReportAsValue(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(objectValue.get("decision")).isEqualTo(Value.of("PERMIT"));
    }

    @Test
    @DisplayName("extracts combining algorithm from a policy-set voter")
    void whenVoterIsPolicySetThenAlgorithmIsExtracted() {
        val report = ReportBuilderUtil.extractReport(traced(createSimplePermitVote()), DUMMY_SUBSCRIPTION_ID,
                DUMMY_SUBSCRIPTION);

        assertThat(report.algorithm()).isNotNull().satisfies(a -> {
            assertThat(a.votingMode()).isEqualTo(VotingMode.PRIORITY_DENY);
        });
    }

    @Test
    @DisplayName("flattens nested policy sets into contributing documents in evaluation order")
    void whenNestedPolicySetsThenAllAreFlattened() {
        val innerPolicyVoter = new PolicyVoterMetadata("inner-policy", "pdp", "config", null, Outcome.DENY, false);
        val innerPolicyVote  = Vote.of(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                innerPolicyVoter);

        val innerSetVoter = new PolicySetVoterMetadata("inner-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.DENY,
                false);
        val innerSetVote  = Vote.combinedVote(AuthorizationDecision.DENY, innerSetVoter, List.of(innerPolicyVote),
                Outcome.DENY);

        val outerSetVoter = new PolicySetVoterMetadata("outer-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.DENY,
                false);
        val vote          = Vote.combinedVote(AuthorizationDecision.DENY, outerSetVoter, List.of(innerSetVote),
                Outcome.DENY);

        val report = ReportBuilderUtil.extractReport(traced(vote), DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.contributingDocuments()).hasSize(2);
        assertThat(report.contributingDocuments().get(0).name()).isEqualTo("inner-set");
        assertThat(report.contributingDocuments().get(1).name()).isEqualTo("inner-set->inner-policy");
    }

    @Test
    @DisplayName("attaches an attribute read to the contributing document whose source contains it")
    void whenAnOccurrenceMatchesADocumentNameThenItsAttributeIsAttachedToThatDocument() {
        val policyVoter = new PolicyVoterMetadata("time-policy", "pdp", "config", null, Outcome.PERMIT, false);
        val policyVote  = Vote.of(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, policyVoter);
        val setVoter    = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.PERMIT,
                false);
        val vote        = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                Outcome.PERMIT);

        val key         = environmentKey("time.now");
        val publishedAt = Instant.parse("2024-01-23T10:30:05.123Z");
        val tracedVote  = new TracedVote(vote, DUMMY_TIMESTAMP,
                Map.of(key, List.of(occurrence("test-set->time-policy"))),
                Map.of(key, new AttributeSnapshot(Value.of("now"), publishedAt)));

        val report = ReportBuilderUtil.extractReport(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.contributingDocuments()).singleElement().satisfies(doc -> {
            assertThat(doc.name()).isEqualTo("test-set->time-policy");
            assertThat(doc.attributes()).singleElement().satisfies(attr -> {
                assertThat(attr.key()).isEqualTo(key);
                assertThat(attr.value()).isEqualTo(Value.of("now"));
                assertThat(attr.valueTimestamp()).isEqualTo(publishedAt);
            });
        });
    }

    @Test
    @DisplayName("does not attach an attribute read whose occurrence belongs to a different document")
    void whenOccurrenceDocumentNameDoesNotMatchThenAttributeIsNotAttached() {
        val policyVoter = new PolicyVoterMetadata("policy-A", "pdp", "config", null, Outcome.PERMIT, false);
        val policyVote  = Vote.of(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, policyVoter);
        val setVoter    = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.PERMIT,
                false);
        val vote        = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                Outcome.PERMIT);

        val key         = environmentKey("time.now");
        val publishedAt = Instant.parse("2024-01-23T10:30:05.123Z");
        val tracedVote  = new TracedVote(vote, DUMMY_TIMESTAMP, Map.of(key, List.of(occurrence("policy-B"))),
                Map.of(key, new AttributeSnapshot(Value.of("now"), publishedAt)));

        val report = ReportBuilderUtil.extractReport(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.contributingDocuments()).singleElement()
                .satisfies(doc -> assertThat(doc.attributes()).isEmpty());
    }

    @Test
    @DisplayName("renders attributes inside the document's JSON entry, not at the report root")
    void whenToObjectValueWithAttributedDocumentThenAttributesAppearInsideDocument() {
        val policyVoter = new PolicyVoterMetadata("time-policy", "pdp", "config", null, Outcome.PERMIT, false);
        val policyVote  = Vote.of(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, policyVoter);
        val setVoter    = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.PERMIT,
                false);
        val vote        = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                Outcome.PERMIT);

        val key        = environmentKey("time.now");
        val tracedVote = new TracedVote(vote, DUMMY_TIMESTAMP,
                Map.of(key, List.of(occurrence("test-set->time-policy"))),
                Map.of(key, new AttributeSnapshot(Value.of("now"), Instant.parse("2024-01-23T10:30:05Z"))));

        val objectValue = ReportBuilderUtil.extractReportAsValue(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(objectValue.get("attributes")).isNull();
        val docs = (ArrayValue) objectValue.get("contributingDocuments");
        assertThat(docs.size()).isEqualTo(1);
        val doc = (ObjectValue) docs.get(0);
        assertThat(doc.get("attributes")).isNotNull();
    }

    @Test
    @DisplayName("attributes a child policy's attribute read under the qualified setname->policyname entry")
    void whenChildPolicyOfSetReadsAttributeThenAttributedUnderQualifiedName() {
        val policyVoter = new PolicyVoterMetadata("p1", "pdp", "config", null, Outcome.PERMIT, false);
        val policyVote  = Vote.of(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, policyVoter);
        val setVoter    = new PolicySetVoterMetadata("s1", "pdp", "config", null, DENY_OVERRIDES, Outcome.PERMIT,
                false);
        val vote        = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                Outcome.PERMIT);

        val key         = environmentKey("time.now");
        val publishedAt = Instant.parse("2024-01-23T10:30:05.123Z");
        val tracedVote  = new TracedVote(vote, DUMMY_TIMESTAMP, Map.of(key, List.of(occurrence("s1->p1"))),
                Map.of(key, new AttributeSnapshot(Value.of("now"), publishedAt)));

        val report = ReportBuilderUtil.extractReport(tracedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);

        assertThat(report.contributingDocuments()).filteredOn(doc -> "s1->p1".equals(doc.name())).singleElement()
                .satisfies(doc -> assertThat(doc.attributes()).singleElement().satisfies(attr -> {
                    assertThat(attr.key()).isEqualTo(key);
                    assertThat(attr.value()).isEqualTo(Value.of("now"));
                    assertThat(attr.valueTimestamp()).isEqualTo(publishedAt);
                }));
    }

    private TracedVote traced(Vote vote) {
        return TracedVote.of(vote, DUMMY_TIMESTAMP);
    }

    private Vote createSimplePermitVote() {
        val voter = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        return new Vote(AuthorizationDecision.PERMIT, List.of(), List.of(), voter, Outcome.PERMIT);
    }

    private static SubscriptionKey environmentKey(String attributeName) {
        val invocation = new AttributeFinderInvocation("test-pdp", "test-config", attributeName, List.of(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(1), 3, false, EMPTY_CTX);
        return new SubscriptionKey(invocation, false);
    }

    private static Occurrence occurrence(String documentName) {
        return new Occurrence(new SourceLocation(documentName, null, 0, 0, 1, 1, 1, 1));
    }
}
