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

import java.util.HashSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.interpreter.Trace;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ReportBuilderUtil {

    public static final String ATTRIBUTES                 = "attributes";
    public static final String ATTRIBUTE_NAME             = "attributeName";
    public static final String ARGUMENTS                  = "arguments";
    public static final String AUTHORIZATION_DECISION     = "authorizationDecision";
    public static final String AUTHORIZATION_SUBSCRIPTION = "authorizationSubscription";
    public static final String COMBINING_ALGORITHM        = "combiningAlgorithm";
    public static final String DOCUMENT_NAME              = "documentName";
    public static final String DOCUMENT_REPORTS           = "documentReports";
    public static final String DOCUMENT_TYPE              = "documentType";
    public static final String ENTITLEMENT                = "entitlement";
    public static final String ERRORS                     = "errors";
    public static final String ERROR_MESSAGE              = "errorMessage";
    public static final String EVALUATED_POLICIES         = "evaluatedPolicies";
    public static final String MATCHING_DOCUMENTS         = "matchingDocuments";
    public static final String METADATA                   = "metadata";
    public static final String MODIFICATIONS              = "modifications";
    public static final String PDP_COMBINING_ALGORITHM    = "pdpCombiningAlgorithm";
    public static final String POLICY                     = "policy";
    public static final String POLICY_SET                 = "policy set";
    public static final String TARGET                     = "target";
    public static final String TIMESTAMP                  = "timestamp";
    public static final String VALUE                      = "value";
    public static final String WHERE                      = "where";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public static JsonNode reduceTraceToReport(JsonNode trace) {
        final var report = JSON.objectNode();
        report.set(AUTHORIZATION_SUBSCRIPTION, trace.get(Trace.AUTHORIZATION_SUBSCRIPTION));
        report.set(AUTHORIZATION_DECISION, trace.get(Trace.AUTHORIZATION_DECISION));
        report.set(TIMESTAMP, trace.get(Trace.TIMESTAMP));
        final var combinedDecision = trace.get(Trace.COMBINED_DECISION);
        if (combinedDecision != null) {
            final var pdpCombiningAlgorithm = combinedDecision.get(Trace.COMBINING_ALGORITHM);
            report.set(PDP_COMBINING_ALGORITHM, pdpCombiningAlgorithm);
            if (combinedDecision.get(Trace.ERROR_MESSAGE) != null) {
                report.set(ERROR_MESSAGE, combinedDecision.get(Trace.ERROR_MESSAGE));
            }
        }
        report.set(MATCHING_DOCUMENTS, trace.get(Trace.MATCHING_DOCUMENTS));
        if (trace.get(Trace.MODIFICATIONS) != null) {
            report.set(MODIFICATIONS, trace.get(Trace.MODIFICATIONS));
        }

        if (combinedDecision == null) {
            return report;
        }

        final var evaluatedPolices = combinedDecision.get(Trace.EVALUATED_POLICIES);
        if (evaluatedPolices != null && evaluatedPolices.isArray() && !evaluatedPolices.isEmpty()) {
            report.set(DOCUMENT_REPORTS, documentReports(evaluatedPolices));
        }
        if (trace.get(Trace.METADATA) != null) {
            report.set(METADATA, trace.get(Trace.METADATA));
        }
        return report;
    }

    private JsonNode documentReports(JsonNode evaluatedPolices) {
        final var documentReports = JSON.arrayNode();
        for (var documentTrace : evaluatedPolices) {
            final var documentType = documentTrace.get(DOCUMENT_TYPE);
            if (documentType != null && documentType.isTextual()) {
                final var type = documentType.asText();
                if (POLICY.equals(type)) {
                    documentReports.add(policyReport(documentTrace));
                }
                if (POLICY_SET.equals(type)) {
                    documentReports.add(policySetReport(documentTrace));
                }
            }
        }
        return documentReports;
    }

    private JsonNode policySetReport(JsonNode documentTrace) {
        final var report = JSON.objectNode();
        report.set(DOCUMENT_TYPE, JSON.textNode(POLICY_SET));
        report.set(DOCUMENT_NAME, documentTrace.get(Trace.POLICY_SET_NAME));
        if (documentTrace.has(Trace.ERROR_MESSAGE)) {
            report.set(ERROR_MESSAGE, documentTrace.get(Trace.ERROR_MESSAGE));
        }
        if (documentTrace.has(Trace.TARGET)) {
            report.set(TARGET, valueReport(documentTrace.get(Trace.TARGET)));
        }
        final var combinedDecision = documentTrace.get(Trace.COMBINED_DECISION);
        if (combinedDecision == null)
            return report;
        report.set(AUTHORIZATION_DECISION, combinedDecision.get(Trace.AUTHORIZATION_DECISION));
        report.set(COMBINING_ALGORITHM, combinedDecision.get(Trace.COMBINING_ALGORITHM));
        final var evaluatedPoliciesReports = policiesReports(combinedDecision);
        if (!evaluatedPoliciesReports.isEmpty()) {
            report.set(EVALUATED_POLICIES, evaluatedPoliciesReports);
        }
        return report;
    }

    private JsonNode policiesReports(JsonNode combinedDecision) {
        final var reports           = JSON.arrayNode();
        final var evaluatedPolicies = combinedDecision.get(Trace.EVALUATED_POLICIES);
        if (evaluatedPolicies == null)
            return reports;
        for (var policyResult : evaluatedPolicies) {
            reports.add(policyReport(policyResult));
        }
        return reports;
    }

    private JsonNode policyReport(JsonNode documentTrace) {
        final var report = JSON.objectNode();
        report.set(DOCUMENT_TYPE, JSON.textNode(POLICY));
        report.set(DOCUMENT_NAME, documentTrace.get(Trace.POLICY_NAME));
        report.set(ENTITLEMENT, documentTrace.get(Trace.ENTITLEMENT));
        if (documentTrace.has(Trace.ERROR_MESSAGE)) {
            report.set(ERROR_MESSAGE, documentTrace.get(Trace.ERROR_MESSAGE));
        }
        if (documentTrace.has(Trace.TARGET)) {
            report.set(TARGET, valueReport(documentTrace.get(Trace.TARGET)));
        }
        if (documentTrace.has(Trace.WHERE)) {
            report.set(WHERE, valueReport(documentTrace.get(Trace.WHERE)));
        }
        report.set(AUTHORIZATION_DECISION, documentTrace.get(Trace.AUTHORIZATION_DECISION));
        final var errors = collectErrors(documentTrace);
        if (!errors.isEmpty()) {
            report.set(ERRORS, errors);
        }
        final var attributes = collectAttributes(documentTrace);
        if (!attributes.isEmpty()) {
            report.set(ATTRIBUTES, attributes);
        }
        return report;
    }

    private JsonNode valueReport(JsonNode jsonNode) {
        if (!jsonNode.isObject())
            return JSON.textNode("Reporting error: Val was not represented as JSON Object. Was: " + jsonNode);
        return jsonNode.get(Trace.VALUE);
    }

    public JsonNode collectErrors(JsonNode trace) {
        final var errorSet = new HashSet<String>();
        collectErrors(trace, errorSet);
        final var arrayNode = JSON.arrayNode();
        errorSet.forEach(element -> arrayNode.add(JSON.textNode(element)));
        return arrayNode;
    }

    private void collectErrors(JsonNode trace, HashSet<String> errorSet) {
        if (trace.isArray() || trace.isObject()) {
            trace.forEach(element -> collectErrors(element, errorSet));
        } else if (trace.isTextual() && trace.asText().startsWith("|ERROR|")) {
            errorSet.add(trace.asText());
        }
    }

    public JsonNode collectAttributes(JsonNode trace) {
        final var arrayNode = JSON.arrayNode();
        collectAttributes(trace, arrayNode);
        return arrayNode;
    }

    private void collectAttributes(JsonNode trace, ArrayNode attributesCollection) {
        if (isAttribute(trace)) {
            attributesCollection.add(attributeReport(trace));
        }
        if (trace.isArray() || trace.isObject()) {
            trace.forEach(element -> collectAttributes(element, attributesCollection));
        }
    }

    private JsonNode attributeReport(JsonNode trace) {
        final var report = JSON.objectNode();
        report.set(VALUE, trace.get(Trace.VALUE));
        report.set(ATTRIBUTE_NAME, attributeName(trace));
        final var timestamp = attributeTimestamp(trace);
        if (timestamp != null)
            report.set(TIMESTAMP, timestamp);
        final var arguments = attributeArguments(trace);
        if (!arguments.isEmpty())
            report.set(ARGUMENTS, arguments);
        return report;
    }

    private ArrayNode attributeArguments(JsonNode attribute) {
        final var result = JSON.arrayNode();
        final var trace  = attribute.get(Trace.TRACE_KEY);
        if (trace == null)
            return result;
        final var arguments = trace.get(Trace.ARGUMENTS_KEY);
        if (arguments == null)
            return result;

        final var i = 0;
        while (arguments.has(Trace.ARGUMENT + "[+" + i + "]")) {
            final var argument = arguments.get(Trace.ARGUMENT + "[+" + i + "]");
            if (argument != null && argument.isObject() && argument.has(Trace.VALUE))
                result.add(argument.get(Trace.VALUE));
        }
        return result;
    }

    private JsonNode attributeTimestamp(JsonNode attribute) {
        final var trace = attribute.get(Trace.TRACE_KEY);
        if (trace == null)
            return null;
        final var arguments = trace.get(Trace.ARGUMENTS_KEY);
        if (arguments == null)
            return null;
        return arguments.get(Trace.TIMESTAMP);
    }

    private TextNode attributeName(JsonNode attribute) {
        final var trace = attribute.get(Trace.TRACE_KEY);
        if (trace == null)
            return JSON.textNode("Reporting Error: No trace of attribute.");
        final var arguments = trace.get(Trace.ARGUMENTS_KEY);
        if (arguments == null)
            return JSON.textNode("Reporting Error: No trace arguments.");
        final var attributeName = arguments.get(Trace.ATTRIBUTE);
        if (attributeName == null)
            return JSON.textNode("Reporting Error: No attribute name.");
        final var attributeNameValue = attributeName.get(Trace.VALUE);
        if (attributeNameValue == null)
            return JSON.textNode("Reporting Error: No attribute name value.");
        return JSON.textNode("<" + attributeNameValue.asText() + ">");
    }

    private boolean isAttribute(JsonNode node) {
        if (!node.isObject())
            return false;

        if (!node.has(Trace.TRACE_KEY))
            return false;

        final var trace = node.get(Trace.TRACE_KEY);
        if (!trace.has(Trace.OPERATOR))
            return false;

        return AttributeContext.class.getSimpleName().equals(trace.get(Trace.OPERATOR).asText());
    }
}
