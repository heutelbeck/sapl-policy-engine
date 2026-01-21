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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.CombiningAlgorithm;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.CombiningAlgorithm.VotingMode;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.compiler.pdp.Vote;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportBuilderUtil")
class ReportBuilderUtilTests {

    private static final CombiningAlgorithm DENY_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    @Test
    @DisplayName("extracts decision from vote")
    void whenExtractReport_thenDecisionIsExtracted() {
        val vote = createSimplePermitVote();

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.decision()).isEqualTo(Decision.PERMIT);
    }

    @Test
    @DisplayName("extracts PDP metadata from vote")
    void whenExtractReport_thenPdpMetadataIsExtracted() {
        val vote = createSimplePermitVote();

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.pdpId()).isEqualTo("cthulhu-pdp");
        assertThat(report.configurationId()).isEqualTo("test-security");
    }

    @Test
    @DisplayName("extracts obligations from vote")
    void whenExtractReport_thenObligationsAreExtracted() {
        val obligation    = Value.of("log_access");
        val obligations   = ArrayValue.builder().add(obligation).build();
        val authzDecision = new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, null);
        val voter         = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, true);
        val vote          = new Vote(authzDecision, List.of(), List.of(), List.of(), voter, Outcome.PERMIT);

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.obligations()).contains(obligation);
    }

    @Test
    @DisplayName("extracts contributing documents from nested votes")
    void whenVoteHasContributingVotes_thenContributingDocumentsAreExtracted() {
        val policyVoter = new PolicyVoterMetadata("forbidden-knowledge-access", "cthulhu-pdp", "test-security", "doc-1",
                Outcome.PERMIT, false);
        val policyVote  = Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null, policyVoter,
                List.of());

        val setVoter = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        val vote     = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote), Outcome.PERMIT);

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.contributingDocuments()).hasSize(1);
        assertThat(report.contributingDocuments().getFirst().name()).isEqualTo("forbidden-knowledge-access");
        assertThat(report.contributingDocuments().getFirst().decision()).isEqualTo(Decision.PERMIT);
    }

    @Test
    @DisplayName("handles vote with no contributing votes")
    void whenVoteHasNoContributingVotes_thenReportHasEmptyContributingDocuments() {
        val voter = new PolicySetVoterMetadata("minimal-set", "minimal-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.DENY, false);
        val vote  = new Vote(AuthorizationDecision.INDETERMINATE, List.of(), List.of(), List.of(), voter, Outcome.DENY);

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.decision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(report.contributingDocuments()).isEmpty();
    }

    @Test
    @DisplayName("converts report to ObjectValue for JSON serialization")
    void whenToObjectValue_thenReportIsConvertedToObjectValue() {
        val vote   = createSimplePermitVote();
        val report = ReportBuilderUtil.extractReport(vote);

        val objectValue = ReportBuilderUtil.toObjectValue(report);

        assertThat(objectValue.get("decision")).isEqualTo(Value.of("PERMIT"));
        assertThat(objectValue.get("pdpId")).isEqualTo(Value.of("cthulhu-pdp"));
    }

    @Test
    @DisplayName("extractReportAsValue combines extraction and conversion")
    void whenExtractReportAsValue_thenReturnsObjectValue() {
        val vote = createSimplePermitVote();

        val objectValue = ReportBuilderUtil.extractReportAsValue(vote);

        assertThat(objectValue.get("decision")).isEqualTo(Value.of("PERMIT"));
    }

    @Test
    @DisplayName("extracts algorithm from policy set voter")
    void whenVoterIsPolicySet_thenAlgorithmIsExtracted() {
        val vote = createSimplePermitVote();

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.algorithm()).isNotNull();
        assertThat(report.algorithm().votingMode()).isEqualTo(VotingMode.PRIORITY_DENY);
    }

    @Test
    @DisplayName("flattens nested policy sets into contributing documents")
    void whenNestedPolicySets_thenAllAreFlattened() {
        val innerPolicyVoter = new PolicyVoterMetadata("inner-policy", "pdp", "config", null, Outcome.DENY, false);
        val innerPolicyVote  = Vote.tracedVote(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, null,
                innerPolicyVoter, List.of());

        val innerSetVoter = new PolicySetVoterMetadata("inner-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.DENY,
                false);
        val innerSetVote  = Vote.combinedVote(AuthorizationDecision.DENY, innerSetVoter,
                List.of(innerPolicyVote), Outcome.DENY);

        val outerSetVoter = new PolicySetVoterMetadata("outer-set", "pdp", "config", null, DENY_OVERRIDES, Outcome.DENY,
                false);
        val vote          = Vote.combinedVote(AuthorizationDecision.DENY, outerSetVoter,
                List.of(innerSetVote), Outcome.DENY);

        val report = ReportBuilderUtil.extractReport(vote);

        assertThat(report.contributingDocuments()).hasSize(2);
        assertThat(report.contributingDocuments().get(0).name()).isEqualTo("inner-set");
        assertThat(report.contributingDocuments().get(1).name()).isEqualTo("inner-policy");
    }

    private Vote createSimplePermitVote() {
        val voter = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        return new Vote(AuthorizationDecision.PERMIT, List.of(), List.of(), List.of(), voter, Outcome.PERMIT);
    }
}
