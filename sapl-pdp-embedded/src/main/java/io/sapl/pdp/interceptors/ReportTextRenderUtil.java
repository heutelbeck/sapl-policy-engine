/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ReportTextRenderUtil {

    private static final String DECISION = "Decision    : ";

    public static String textReport(JsonNode jsonReport, boolean prettyPrint, ObjectMapper mapper) {
        StringBuilder report = new StringBuilder("--- The PDP made a decision ---\n");
        report.append("Subscription: ").append(prettyPrint ? '\n' : "").append(
                prettyPrintJson(jsonReport.get(ReportBuilderUtil.AUTHORIZATION_SUBSCRIPTION), prettyPrint, mapper))
                .append('\n');
        report.append(DECISION).append(prettyPrint ? '\n' : "")
                .append(prettyPrintJson(jsonReport.get(ReportBuilderUtil.AUTHORIZATION_DECISION), prettyPrint, mapper))
                .append('\n');
        report.append("Timestamp   : ").append(jsonReport.get(ReportBuilderUtil.TIMESTAMP).textValue()).append('\n');
        report.append("Algorithm   : ").append(jsonReport.get(ReportBuilderUtil.PDP_COMBINING_ALGORITHM)).append('\n');
        var topLevelError = jsonReport.get(ReportBuilderUtil.ERROR_MESSAGE);
        if (topLevelError != null) {
            report.append("PDP Error   : ").append(topLevelError).append('\n');
        }
        var matchingDocuments = jsonReport.get(ReportBuilderUtil.MATCHING_DOCUMENTS);
        if (matchingDocuments == null || !matchingDocuments.isArray() || matchingDocuments.isEmpty()) {
            report.append(
                    "Matches     : NONE (i.e.,no policies/policy sets were set, or all target expressions evaluated to false or error.)\n");
        } else {
            report.append("Matches     : ").append(matchingDocuments).append('\n');
        }
        var modifications = jsonReport.get(ReportBuilderUtil.MODIFICATIONS);
        if (modifications != null && modifications.isArray() && !modifications.isEmpty()) {
            report.append("There were interceptors invoked after the PDP which changed the decision:\n");
            for (var mod : modifications) {
                report.append(" - ").append(mod).append('\n');
            }
        }
        var documentReports = jsonReport.get(ReportBuilderUtil.DOCUMENT_REPORTS);
        if (documentReports == null || !documentReports.isArray() || documentReports.isEmpty()) {
            report.append("No policy or policy sets have been evaluated\n");
            return report.toString();
        }

        for (var documentReport : documentReports) {
            report.append(documentReport(documentReport));
        }

        var metadata = jsonReport.get(ReportBuilderUtil.METADATA);
        if (metadata != null) {
            report.append("Metadata    : ").append(
                    prettyPrintJson(jsonReport.get(ReportBuilderUtil.AUTHORIZATION_SUBSCRIPTION), prettyPrint, mapper))
                    .append("\n");
            return report.toString();
        }
        return report.toString();
    }

    private static String documentReport(JsonNode documentReport) {
        var documentType = documentReport.get(ReportBuilderUtil.DOCUMENT_TYPE);
        if (documentType == null)
            return "Reporting error. Unknown documentType: " + documentReport + '\n';
        var type = documentType.asText();
        if ("policy set".equals(type))
            return policySetReport(documentReport);
        if ("policy".equals(type))
            return policyReport(documentReport);

        return "Reporting error. Unknown documentType: " + type + '\n';
    }

    private static String policyReport(JsonNode policy) {
        var report = "Policy Evaluation Result ===================\n";
        report += "Name        : " + policy.get(ReportBuilderUtil.DOCUMENT_NAME) + '\n';
        report += "Entitlement : " + policy.get(ReportBuilderUtil.ENTITLEMENT) + '\n';
        report += DECISION + policy.get(ReportBuilderUtil.AUTHORIZATION_DECISION) + '\n';
        if (policy.has("target"))
            report += "Target      : " + policy.get(ReportBuilderUtil.TARGET) + '\n';
        if (policy.has("where"))
            report += "Where       : " + policy.get(ReportBuilderUtil.WHERE) + '\n';
        report += errorReport(policy.get(ReportBuilderUtil.ERRORS));
        report += attributeReport(policy.get(ReportBuilderUtil.ATTRIBUTES));
        return report;
    }

    private static String errorReport(JsonNode errors) {
        var report = new StringBuilder();
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return report.toString();
        }
        report.append("Errors during evaluation:\n");
        for (var error : errors) {
            report.append(" - ");
            report.append(error);
            report.append('\n');
        }
        return report.toString();
    }

    private static String attributeReport(JsonNode attributes) {
        var report = new StringBuilder();
        if (attributes == null || !attributes.isArray() || attributes.isEmpty()) {
            return report.toString();
        }
        report.append("Policy Information Point Data:\n");
        for (var attribute : attributes) {
            report.append(" - ");
            report.append(attribute);
            report.append('\n');
        }
        return report.toString();
    }

    private static String policySetReport(JsonNode policySet) {
        StringBuilder report = new StringBuilder("Policy Set Evaluation Result ===============\n");
        report.append("Name        : ").append(policySet.get(ReportBuilderUtil.DOCUMENT_NAME)).append('\n');
        report.append("Algorithm   : ").append(policySet.get(ReportBuilderUtil.COMBINING_ALGORITHM)).append('\n');
        report.append(DECISION).append(policySet.get(ReportBuilderUtil.AUTHORIZATION_DECISION)).append('\n');
        if (policySet.has("target"))
            report.append("Target      : ").append(policySet.get(ReportBuilderUtil.TARGET)).append('\n');
        if (policySet.has("errorMessage"))
            report.append("Error       : ").append(policySet.get(ReportBuilderUtil.ERROR_MESSAGE)).append('\n');
        var evaluatedPolicies = policySet.get(ReportBuilderUtil.EVALUATED_POLICIES);
        if (evaluatedPolicies != null && evaluatedPolicies.isArray()) {
            for (var policyReport : evaluatedPolicies) {
                report.append(indentText("   |", policyReport(policyReport)));
            }
        }
        return report.toString();
    }

    private String indentText(String indentation, String text) {
        StringBuilder indented = new StringBuilder();
        for (var line : text.split("\n")) {
            indented.append(indentation).append(line.replace("\r", "")).append('\n');
        }
        return indented.toString();
    }

    public static String prettyPrintJson(JsonNode json, boolean prettyPrint, ObjectMapper mapper) {
        if (!prettyPrint)
            return json.toString();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            log.error("Pretty print JSON failed: ", e);
            return json.toString();
        }
    }

}
