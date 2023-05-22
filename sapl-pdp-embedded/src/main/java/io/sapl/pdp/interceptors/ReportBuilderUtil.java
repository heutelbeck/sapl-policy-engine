/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.PDPDecision;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ReportBuilderUtil {
	public static final String           PDP_COMBINING_ALGORITHM = "pdpCombiningAlgorithm";
	private static final JsonNodeFactory JSON                    = JsonNodeFactory.instance;

	public static JsonNode reduceTraceToReport(JsonNode trace) {
		var report = JSON.objectNode();
		report.set(PDPDecision.AUTHORIZATION_SUBSCRIPTION, trace.get(PDPDecision.AUTHORIZATION_SUBSCRIPTION));
		report.set(PDPDecision.AUTHORIZATION_DECISION, trace.get(PDPDecision.AUTHORIZATION_DECISION));
		report.set(PDPDecision.TIMESTAMP_STRING, trace.get(PDPDecision.TIMESTAMP_STRING));
		var combinedDecision = trace.get(PDPDecision.COMBINED_DECISION);
		if (combinedDecision != null) {
			var pdpCombiningAlgorithm = combinedDecision.get(CombinedDecision.COMBINING_ALGORITHM);
			report.set(PDP_COMBINING_ALGORITHM, pdpCombiningAlgorithm);
			if (combinedDecision.get(CombinedDecision.ERROR) != null) {
				report.set(CombinedDecision.ERROR, combinedDecision.get(CombinedDecision.ERROR));
			}
		}
		report.set(PDPDecision.MATCHING_DOCUMENTS, trace.get(PDPDecision.MATCHING_DOCUMENTS));
		if (trace.get(PDPDecision.MODIFICATIONS_STRING) != null) {
			report.set(PDPDecision.MODIFICATIONS_STRING, trace.get(PDPDecision.MODIFICATIONS_STRING));
		}
		
		if(combinedDecision == null) {
			return report;
		}
			
		var evaluatedPolices = combinedDecision.get(CombinedDecision.EVALUATED_POLICIES);
		if (evaluatedPolices != null && evaluatedPolices.isArray() && evaluatedPolices.size() > 0) {
			report.set("documentReports", documentReports(evaluatedPolices));
		}
		return report;
	}

	private JsonNode documentReports(JsonNode evaluatedPolices) {
		var documentReports = JSON.arrayNode();
		for (var documentTrace : evaluatedPolices) {
			var documentType = documentTrace.get("documentType");
			if (documentType != null && documentType.isTextual()) {
				var type = documentType.asText();
				if ("policy".equals(type)) {
					documentReports.add(policyReport(documentTrace));
				}
				if ("policy set".equals(type)) {
					documentReports.add(policySetReport(documentTrace));
				}
			}
		}
		return documentReports;
	}

	private JsonNode policySetReport(JsonNode documentTrace) {
		var report = JSON.objectNode();
		report.set("documentType", JSON.textNode("policy set"));
		report.set("documentName", documentTrace.get("policySetName"));
		if (documentTrace.has("errorMessage")) {
			report.set("errorMessage", documentTrace.get("errorMessage"));
		}
		if (documentTrace.has("target")) {
			report.set("target", valueReport(documentTrace.get("target")));
		}
		var combinedDecision = documentTrace.get("combinedDecision");
		if (combinedDecision == null)
			return report;
		report.set(CombinedDecision.AUTHORIZATION_DECISION,
				combinedDecision.get(CombinedDecision.AUTHORIZATION_DECISION));
		report.set(CombinedDecision.COMBINING_ALGORITHM, combinedDecision.get(CombinedDecision.COMBINING_ALGORITHM));
		var evaluatedPoliciesReports = policiesReports(combinedDecision);
		if (!evaluatedPoliciesReports.isEmpty()) {
			report.set("evaluatedPolicies", evaluatedPoliciesReports);
		}
		return report;
	}

	private JsonNode policiesReports(JsonNode combinedDecision) {
		var reports           = JSON.arrayNode();
		var evaluatedPolicies = combinedDecision.get(CombinedDecision.EVALUATED_POLICIES);
		if (evaluatedPolicies == null)
			return reports;
		for (var policyResult : evaluatedPolicies) {
			reports.add(policyReport(policyResult));
		}
		return reports;
	}

	private JsonNode policyReport(JsonNode documentTrace) {
		var report = JSON.objectNode();
		report.set("documentType", JSON.textNode("policy"));
		report.set("documentName", documentTrace.get("policyName"));
		report.set("entitlement", documentTrace.get("entitlement"));
		if (documentTrace.has("errorMessage")) {
			report.set("errorMessage", documentTrace.get("errorMessage"));
		}
		if (documentTrace.has("target")) {
			report.set("target", valueReport(documentTrace.get("target")));
		}
		if (documentTrace.has("where")) {
			report.set("where", valueReport(documentTrace.get("where")));
		}
		report.set(PDPDecision.AUTHORIZATION_DECISION, documentTrace.get(PDPDecision.AUTHORIZATION_DECISION));
		var errors = collectErrors(documentTrace);
		if (!errors.isEmpty()) {
			report.set("errors", errors);
		}
		var attributes = collectAttributes(documentTrace);
		if (!attributes.isEmpty()) {
			report.set("attributes", attributes);
		}
		return report;
	}

	private JsonNode valueReport(JsonNode jsonNode) {
		if (!jsonNode.isObject())
			return JSON.textNode("Reporting error: Val was not represented as JSON Object. Was: " + jsonNode);
		return jsonNode.get("value");
	}

	public JsonNode collectErrors(JsonNode trace) {
		var errorSet = new HashSet<String>();
		collectErrors(trace, errorSet);
		var arrayNode = JSON.arrayNode();
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
		var arrayNode = JSON.arrayNode();
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
		var report = JSON.objectNode();
		report.set("value", trace.get("value"));
		report.set("attributeName", attributeName(trace));
		var timestamp = attributeTimestamp(trace);
		if (timestamp != null)
			report.set("timestamp", timestamp);
		var arguments = attributeArguments(trace);
		if (!arguments.isEmpty())
			report.set("arguments", arguments);
		return report;
	}

	private ArrayNode attributeArguments(JsonNode attribute) {
		var result = JSON.arrayNode();
		var trace  = attribute.get("trace");
		if (trace == null)
			return result;
		var arguments = trace.get("arguments");
		if (arguments == null)
			return result;

		var i = 0;
		while (arguments.has("argument[+" + i + "]")) {
			var argument = arguments.get("argument[+" + i + "]");
			if (argument != null && argument.isObject() && argument.has("value"))
				result.add(argument.get("value"));
		}
		return result;
	}

	private JsonNode attributeTimestamp(JsonNode attribute) {
		var trace = attribute.get("trace");
		if (trace == null)
			return null;
		var arguments = trace.get("arguments");
		if (arguments == null)
			return null;
		return arguments.get("timestamp");
	}

	private TextNode attributeName(JsonNode attribute) {
		var trace = attribute.get("trace");
		if (trace == null)
			return JSON.textNode("Reporting Error: No trace of attribute.");
		var arguments = trace.get("arguments");
		if (arguments == null)
			return JSON.textNode("Reporting Error: No trace arguments.");
		var attributeName = arguments.get("attribute");
		if (attributeName == null)
			return JSON.textNode("Reporting Error: No attribute name.");
		var attributeNameValue = attributeName.get("value");
		if (attributeNameValue == null)
			return JSON.textNode("Reporting Error: No attribute name value.");
		return JSON.textNode("<" + attributeNameValue.asText() + ">");
	}

	private boolean isAttribute(JsonNode node) {
		if (!node.isObject())
			return false;

		if (!node.has("trace"))
			return false;

		var trace = node.get("trace");
		if (!trace.has("operator"))
			return false;

		return AttributeContext.class.getSimpleName().equals(trace.get("operator").asText());
	}
}
