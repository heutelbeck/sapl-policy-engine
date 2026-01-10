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
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.traced.TraceFields;
import io.sapl.api.pdp.traced.TracedDecision;
import io.sapl.api.pdp.traced.TracedPdpDecision;
import io.sapl.compiler.TracedPolicyDecision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportBuilderUtil")
class ReportBuilderUtilTests {

    @Test
    @DisplayName("extracts decision from traced decision")
    void whenExtractReport_thenDecisionIsExtracted() {
        val trace          = createSimplePermitTrace();
        val tracedDecision = new TracedDecision(trace);

        val report = ReportBuilderUtil.extractReport(tracedDecision);

        assertThat(report.get(TraceFields.DECISION)).isEqualTo(Value.of("PERMIT"));
    }

    @Test
    @DisplayName("extracts PDP metadata from trace")
    void whenExtractReport_thenPdpMetadataIsExtracted() {
        val trace          = createSimplePermitTrace();
        val tracedDecision = new TracedDecision(trace);

        val report = ReportBuilderUtil.extractReport(tracedDecision);

        assertThat(report.get(TraceFields.PDP_ID)).isInstanceOf(TextValue.class);
        assertThat(((TextValue) report.get(TraceFields.PDP_ID)).value()).isEqualTo("cthulhu-pdp");
    }

    @Test
    @DisplayName("extracts obligations from trace")
    void whenExtractReport_thenObligationsAreExtracted() {
        val obligation = Value.of("log_access");
        val trace      = TracedPdpDecision.builder().pdpId("cthulhu-pdp").configurationId("test-security")
                .subscriptionId("sub-001").subscription(AuthorizationSubscription.of("cultist", "summon", "elder-god"))
                .timestamp(Instant.now().toString()).algorithm("deny-overrides").decision(Decision.PERMIT)
                .totalDocuments(1).obligations(List.of(obligation)).build();

        val tracedDecision = new TracedDecision(trace);
        val report         = ReportBuilderUtil.extractReport(tracedDecision);

        val obligations = report.get(TraceFields.OBLIGATIONS);
        assertThat(obligations).isInstanceOf(ArrayValue.class);
        assertThat(((ArrayValue) obligations)).contains(obligation);
    }

    @Test
    @DisplayName("includes modifications when present")
    void whenTracedDecisionHasModifications_thenModificationsAreIncluded() {
        val trace          = createSimplePermitTrace();
        val tracedDecision = new TracedDecision(trace).modified(io.sapl.api.pdp.AuthorizationDecision.DENY,
                "Ritual interrupted by investigators");

        val report = ReportBuilderUtil.extractReport(tracedDecision);

        val modifications = report.get(TraceFields.MODIFICATIONS);
        assertThat(modifications).isInstanceOf(ArrayValue.class);
        assertThat(((ArrayValue) modifications)).hasSize(1);
    }

    @Test
    @DisplayName("extracts document reports from nested policies")
    void whenTraceContainsPolicies_thenDocumentReportsAreExtracted() {
        val policyTrace = TracedPolicyDecision.builder().name("forbidden-knowledge-access").entitlement("permit")
                .decision(Decision.PERMIT).build();

        val trace = TracedPdpDecision.builder().pdpId("cthulhu-pdp").configurationId("test-security")
                .subscriptionId("sub-001").subscription(AuthorizationSubscription.of("cultist", "read", "necronomicon"))
                .timestamp(Instant.now().toString()).algorithm("deny-overrides").decision(Decision.PERMIT)
                .totalDocuments(1).addDocument(policyTrace).build();

        val tracedDecision = new TracedDecision(trace);
        val report         = ReportBuilderUtil.extractReport(tracedDecision);

        val documents = report.get(TraceFields.DOCUMENTS);
        assertThat(documents).isInstanceOf(ArrayValue.class);
        assertThat(((ArrayValue) documents)).hasSize(1);
    }

    @Test
    @DisplayName("extracts subscription from trace")
    void whenExtractReport_thenSubscriptionIsExtracted() {
        val trace          = createSimplePermitTrace();
        val tracedDecision = new TracedDecision(trace);

        val report = ReportBuilderUtil.extractReport(tracedDecision);

        assertThat(report.get(TraceFields.SUBSCRIPTION)).isNotNull();
    }

    @Test
    @DisplayName("handles empty trace gracefully")
    void whenTraceIsMinimal_thenReportIsStillGenerated() {
        val trace = TracedPdpDecision.builder().pdpId("minimal-pdp").configurationId("test-security")
                .subscriptionId("sub-001").subscription(AuthorizationSubscription.of("user", "action", "resource"))
                .timestamp(Instant.now().toString()).algorithm("deny-overrides").decision(Decision.INDETERMINATE)
                .totalDocuments(0).build();

        val tracedDecision = new TracedDecision(trace);
        val report         = ReportBuilderUtil.extractReport(tracedDecision);

        assertThat(report.get(TraceFields.DECISION)).isEqualTo(Value.of("INDETERMINATE"));
    }

    private Value createSimplePermitTrace() {
        return TracedPdpDecision.builder().pdpId("cthulhu-pdp").configurationId("test-security")
                .subscriptionId("sub-001").subscription(AuthorizationSubscription.of("cultist", "summon", "elder-god"))
                .timestamp(Instant.now().toString()).algorithm("deny-overrides").decision(Decision.PERMIT)
                .totalDocuments(1).build();
    }
}
