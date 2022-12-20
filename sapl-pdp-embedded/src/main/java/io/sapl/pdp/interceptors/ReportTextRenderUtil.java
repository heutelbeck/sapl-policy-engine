package io.sapl.pdp.interceptors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.interpreter.CombinedDecision;
import io.sapl.pdp.PDPDecision;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ReportTextRenderUtil {

	public static String textReport(JsonNode jsonReport, boolean prettyPrint, ObjectMapper mapper) {
		var report = "--- The PDP made a decison ---\n";
		report += "Subscription: " + (prettyPrint ? "\n" : "")
				+ prettyPrintJson(jsonReport.get(PDPDecision.AUTHORIZATION_SUBSCRIPTION), prettyPrint, mapper) + "\n";
		report += "Decision    : " + (prettyPrint ? "\n" : "")
				+ prettyPrintJson(jsonReport.get(PDPDecision.AUTHORIZATION_DECISION), prettyPrint, mapper) + "\n";
		report += "Timestamp   : " + jsonReport.get(PDPDecision.TIMESTAMP).textValue() + "\n";
		report += "Algorithm   : " + jsonReport.get(ReportBuilderUtil.PDP_COMBINING_ALGORITHM) + "\n";
		var topLevelError = jsonReport.get(CombinedDecision.ERROR);
		if (topLevelError != null) {
			report += "PDP Error   : " + topLevelError + "\n";
		}
		var matchingDocuments = jsonReport.get(PDPDecision.MATCHING_DOCUMENTS);
		if (matchingDocuments == null || !matchingDocuments.isArray() || matchingDocuments.size() == 0) {
			report += "Matches     : NONE (i.e.,no policies/policy sets were set, or all target expressions evaluated to false or error.)\n";
		} else {
			report += "Matches     : " + matchingDocuments + "\n";
		}
		var modifications = jsonReport.get(PDPDecision.MODIFICATIONS);
		if (modifications != null && modifications.isArray() && modifications.size() != 0) {
			report += "There were interceptors invoked after the PDP which changed the decision:\n";
			for (var mod : modifications) {
				report += " - " + mod + "\n";
			}
		}
		var documentReports = jsonReport.get("documentReports");
		if (documentReports == null || !documentReports.isArray() || documentReports.size() == 0) {
			report += "No policy or policy sets have been evaluated\n";
			return report;
		}

		for (var documentReport : documentReports) {
			report += documentReport(documentReport);
		}
		return report;
	}

	private static String documentReport(JsonNode documentReport) {
		var documentType = documentReport.get("documentType");
		if (documentType == null)
			return "Reporting error. Unknown documentType: " + documentReport + "\n";
		var type = documentType.asText();
		if ("policy set".equals(type))
			return policySetReport(documentReport);
		if ("policy".equals(type))
			return policyReport(documentReport);

		return "Reporting error. Unknown documentType: " + type + "\n";
	}

	private static String policyReport(JsonNode policy) {
		var report = "Policy Evaluation Result ===================\n";
		report += "Name        : " + policy.get("documentName") + "\n";
		report += "Entitlement : " + policy.get("entitlement") + "\n";
		report += "Decision    : " + policy.get(PDPDecision.AUTHORIZATION_DECISION) + "\n";
		if (policy.has("target"))
			report += "Target      : " + policy.get("target") + "\n";
		if (policy.has("where"))
			report += "Where       : " + policy.get("where") + "\n";
		report += errorReport(policy.get("errors"));
		report += attributeReport(policy.get("attributes"));
		return report;
	}

	private static String errorReport(JsonNode errors) {
		var report = "";
		if (errors == null || !errors.isArray() || errors.size() == 0) {
			return report;
		}
		report += "Errors during evaluation:\n";
		for (var error : errors) {
			report += " - " + error + "\n";
		}
		return report;
	}

	private static String attributeReport(JsonNode attributes) {
		var report = "";
		if (attributes == null || !attributes.isArray() || attributes.size() == 0) {
			return report;
		}
		report += "Policy Information Point Data:\n";
		for (var attribute : attributes) {
			report += " - " + attribute + "\n";
		}
		return report;
	}

	private static String policySetReport(JsonNode policySet) {
		var report = "Policy Set Evaluation Result ===============\n";
		report += "Name        : " + policySet.get("documentName") + "\n";
		report += "Algorithm   : " + policySet.get(CombinedDecision.COMBINING_ALGORITHM) + "\n";
		report += "Decision    : " + policySet.get(PDPDecision.AUTHORIZATION_DECISION) + "\n";
		if (policySet.has("target"))
			report += "Target      : " + policySet.get("target") + "\n";
		if (policySet.has("errorMessage"))
			report += "Error       : " + policySet.get("errorMessage") + "\n";
		var evaluatedPolicies = policySet.get("evaluatedPolicies");
		if (evaluatedPolicies != null && evaluatedPolicies.isArray()) {
			for (var policyReport : evaluatedPolicies) {
				report += indentText("   |", policyReport(policyReport));
			}
		}
		return report;
	}

	private String indentText(String indentation, String text) {
		var indented = "";
		for (var line : text.split("\n")) {
			indented += indentation + line.replace("\r", "") + "\n";
		}
		return indented;
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
