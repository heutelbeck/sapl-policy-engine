/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.internal.TraceFields;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportTextRenderUtil")
class ReportTextRenderUtilTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("renders decision in text report")
    void whenTextReport_thenContainsDecision() {
        val report = createSimpleReport("PERMIT");

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Decision    :").contains("PERMIT");
    }

    @Test
    @DisplayName("renders subscription in text report")
    void whenTextReport_thenContainsSubscription() {
        val subscription = ObjectValue.builder().put("subject", Value.of("cultist")).put("action", Value.of("summon"))
                .put("resource", Value.of("elder-god")).build();
        val report       = createReportWithSubscription(subscription);

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Subscription:");
    }

    @Test
    @DisplayName("renders algorithm in text report")
    void whenTextReport_thenContainsAlgorithm() {
        val report = ObjectValue.builder().put(TraceFields.DECISION, Value.of("PERMIT"))
                .put(TraceFields.ALGORITHM, Value.of("deny-overrides")).put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY)
                .put(TraceFields.ADVICE, Value.EMPTY_ARRAY).put(TraceFields.TOTAL_DOCUMENTS, Value.of(5))
                .put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Algorithm   : ").contains("deny-overrides");
    }

    @Test
    @DisplayName("renders document counts in text report")
    void whenTextReport_thenContainsDocumentCounts() {
        val documents = ArrayValue.builder().add(createPolicyDocument("ritual-policy", "permit", "PERMIT")).build();
        val report    = ObjectValue.builder().put(TraceFields.DECISION, Value.of("PERMIT"))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(10)).put(TraceFields.DOCUMENTS, documents).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Documents   : 1 matching out of 10 total");
    }

    @Test
    @DisplayName("renders modifications in text report")
    void whenReportHasModifications_thenModificationsAreRendered() {
        val modifications = ArrayValue.builder().add(Value.of("Ritual interrupted by investigators"))
                .add(Value.of("Elder sign barrier activated")).build();
        val report        = ObjectValue.builder().put(TraceFields.DECISION, Value.of("DENY"))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(1)).put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY)
                .put(TraceFields.MODIFICATIONS, modifications).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Interceptor Modifications:").contains("Ritual interrupted by investigators")
                .contains("Elder sign barrier activated");
    }

    @Test
    @DisplayName("renders policy documents in text report")
    void whenReportHasPolicies_thenPoliciesAreRendered() {
        val documents = ArrayValue.builder().add(createPolicyDocument("forbidden-knowledge-access", "permit", "PERMIT"))
                .build();
        val report    = ObjectValue.builder().put(TraceFields.DECISION, Value.of("PERMIT"))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(1)).put(TraceFields.DOCUMENTS, documents).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("=== Document Evaluation Results ===").contains("Policy: forbidden-knowledge-access")
                .contains("Entitlement : permit").contains("Decision    : PERMIT");
    }

    @Test
    @DisplayName("renders retrieval errors in text report")
    void whenReportHasRetrievalErrors_thenErrorsAreRendered() {
        val errors = ArrayValue.builder().add(ObjectValue.builder().put(TraceFields.NAME, Value.of("cursed-tome"))
                .put(TraceFields.MESSAGE, Value.of("Failed to parse arcane symbols")).build()).build();
        val report = ObjectValue.builder().put(TraceFields.DECISION, Value.of("INDETERMINATE"))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(1)).put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY)
                .put(TraceFields.RETRIEVAL_ERRORS, errors).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("Retrieval Errors:");
    }

    @Test
    @DisplayName("pretty-prints value as JSON")
    void whenPrettyPrint_thenJsonIsFormatted() {
        val value = ObjectValue.builder().put("key", Value.of("value")).build();

        val pretty  = ReportTextRenderUtil.prettyPrintValue(value, true, MAPPER);
        val compact = ReportTextRenderUtil.prettyPrintValue(value, false, MAPPER);

        assertThat(pretty).contains("\n");
        assertThat(compact).doesNotContain("\n");
    }

    @Test
    @DisplayName("renders empty documents message when no policies evaluated")
    void whenNoDocuments_thenEmptyMessageIsRendered() {
        val report = ObjectValue.builder().put(TraceFields.DECISION, Value.of("DENY"))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(0)).put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY).build();

        val text = ReportTextRenderUtil.textReport(report, false, MAPPER);

        assertThat(text).contains("No documents were evaluated.");
    }

    private ObjectValue createSimpleReport(String decision) {
        return ObjectValue.builder().put(TraceFields.DECISION, Value.of(decision))
                .put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY).put(TraceFields.ADVICE, Value.EMPTY_ARRAY)
                .put(TraceFields.TOTAL_DOCUMENTS, Value.of(0)).put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY).build();
    }

    private ObjectValue createReportWithSubscription(ObjectValue subscription) {
        return ObjectValue.builder().put(TraceFields.DECISION, Value.of("PERMIT"))
                .put(TraceFields.SUBSCRIPTION, subscription).put(TraceFields.OBLIGATIONS, Value.EMPTY_ARRAY)
                .put(TraceFields.ADVICE, Value.EMPTY_ARRAY).put(TraceFields.TOTAL_DOCUMENTS, Value.of(0))
                .put(TraceFields.DOCUMENTS, Value.EMPTY_ARRAY).build();
    }

    private ObjectValue createPolicyDocument(String name, String entitlement, String decision) {
        return ObjectValue.builder().put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                .put(TraceFields.NAME, Value.of(name)).put(TraceFields.ENTITLEMENT, Value.of(entitlement))
                .put(TraceFields.DECISION, Value.of(decision)).build();
    }
}
