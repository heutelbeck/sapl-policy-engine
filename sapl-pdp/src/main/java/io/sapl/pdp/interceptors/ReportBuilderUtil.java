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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.internal.TraceFields;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import io.sapl.compiler.TracedPolicyDecision;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building concise reports from traced PDP decisions.
 * <p>
 * Extracts key information from the hierarchical trace structure into a flat
 * report structure suitable for logging and auditing.
 */
@UtilityClass
public class ReportBuilderUtil {

    /**
     * Extracts a concise report from a traced decision.
     * <p>
     * The report contains the essential information for understanding the decision:
     * subscription, decision, timestamp, algorithm, document evaluations, and any
     * modifications by interceptors.
     *
     * @param tracedDecision
     * the traced decision to extract the report from
     *
     * @return an ObjectValue containing the concise report
     */
    public static ObjectValue extractReport(TracedDecision tracedDecision) {
        val trace  = tracedDecision.originalTrace();
        val report = ObjectValue.builder();

        // Top-level decision fields
        report.put(TraceFields.DECISION, Value.of(TracedPdpDecision.getDecision(trace).name()));
        report.put(TraceFields.OBLIGATIONS, TracedPdpDecision.getObligations(trace));
        report.put(TraceFields.ADVICE, TracedPdpDecision.getAdvice(trace));

        val resource = TracedPdpDecision.getResource(trace);
        if (resource != null && !(resource instanceof io.sapl.api.model.UndefinedValue)) {
            report.put(TraceFields.RESOURCE, resource);
        }

        // Trace metadata
        val traceObj = TracedPdpDecision.getTrace(trace);
        if (!traceObj.isEmpty()) {
            report.put(TraceFields.PDP_ID, traceObj.get(TraceFields.PDP_ID));
            report.put(TraceFields.SUBSCRIPTION_ID, traceObj.get(TraceFields.SUBSCRIPTION_ID));
            report.put(TraceFields.SUBSCRIPTION, traceObj.get(TraceFields.SUBSCRIPTION));
            report.put(TraceFields.TIMESTAMP, traceObj.get(TraceFields.TIMESTAMP));
            report.put(TraceFields.ALGORITHM, traceObj.get(TraceFields.ALGORITHM));
            report.put(TraceFields.TOTAL_DOCUMENTS, traceObj.get(TraceFields.TOTAL_DOCUMENTS));
        }

        // Document reports
        val documents       = TracedPdpDecision.getDocuments(trace);
        val documentReports = extractDocumentReports(documents);
        if (!documentReports.isEmpty()) {
            report.put(TraceFields.DOCUMENTS, ArrayValue.builder().addAll(documentReports).build());
        }

        // Retrieval errors
        val retrievalErrors = TracedPdpDecision.getRetrievalErrors(trace);
        if (!retrievalErrors.isEmpty()) {
            report.put(TraceFields.RETRIEVAL_ERRORS, retrievalErrors);
        }

        // Modifications from interceptors
        val modifications = tracedDecision.modifications();
        if (!modifications.isEmpty()) {
            val modArray = ArrayValue.builder();
            for (val mod : modifications) {
                modArray.add(Value.of(mod));
            }
            report.put(TraceFields.MODIFICATIONS, modArray.build());
        }

        return report.build();
    }

    private static List<Value> extractDocumentReports(ArrayValue documents) {
        val reports = new ArrayList<Value>();
        for (val document : documents) {
            if (document instanceof ObjectValue docObj) {
                val type = getTextFieldValue(docObj, TraceFields.TYPE);
                if (TraceFields.TYPE_POLICY.equals(type)) {
                    reports.add(extractPolicyReport(docObj));
                } else if (TraceFields.TYPE_SET.equals(type)) {
                    reports.add(extractPolicySetReport(docObj));
                }
            }
        }
        return reports;
    }

    private static ObjectValue extractPolicyReport(ObjectValue policy) {
        val report = ObjectValue.builder();

        report.put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY));
        report.put(TraceFields.NAME, policy.get(TraceFields.NAME));
        report.put(TraceFields.ENTITLEMENT, policy.get(TraceFields.ENTITLEMENT));
        report.put(TraceFields.DECISION, policy.get(TraceFields.DECISION));

        // Include errors if present
        val errors = TracedPolicyDecision.getErrors(policy);
        if (!errors.isEmpty()) {
            report.put(TraceFields.ERRORS, errors);
        }

        // Include attributes if present
        val attributes = TracedPolicyDecision.getAttributes(policy);
        if (!attributes.isEmpty()) {
            report.put(TraceFields.ATTRIBUTES, attributes);
        }

        // Include target error if present
        if (TracedPolicyDecision.hasTargetError(policy)) {
            report.put(TraceFields.TARGET_ERROR, TracedPolicyDecision.getTargetError(policy));
        }

        return report.build();
    }

    private static ObjectValue extractPolicySetReport(ObjectValue policySet) {
        val report = ObjectValue.builder();

        report.put(TraceFields.TYPE, Value.of(TraceFields.TYPE_SET));
        report.put(TraceFields.NAME, policySet.get(TraceFields.NAME));
        report.put(TraceFields.DECISION, policySet.get(TraceFields.DECISION));
        report.put(TraceFields.ALGORITHM, policySet.get(TraceFields.ALGORITHM));

        // Include nested policies
        val policies = policySet.get(TraceFields.POLICIES);
        if (policies instanceof ArrayValue policiesArray && !policiesArray.isEmpty()) {
            val nestedReports = extractDocumentReports(policiesArray);
            if (!nestedReports.isEmpty()) {
                report.put(TraceFields.POLICIES, ArrayValue.builder().addAll(nestedReports).build());
            }
        }

        return report.build();
    }

    private static String getTextFieldValue(ObjectValue obj, String fieldName) {
        val field = obj.get(fieldName);
        if (field instanceof TextValue textValue) {
            return textValue.value();
        }
        return null;
    }
}
