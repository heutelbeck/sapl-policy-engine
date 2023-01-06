package io.sapl.pdp.interceptors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ReportingDecisionInterceptor implements TracedDecisionInterceptor {

	private final ObjectMapper mapper;

	private final boolean prettyPrint;
	private final boolean printTrace;
	private final boolean printJsonReport;
	private final boolean printTextReport;

	@Override
	public Integer getPriority() {
		// Always report last
		return Integer.MIN_VALUE;
	}

	@Override
	public TracedDecision apply(TracedDecision tracedDecision) {
		var trace = tracedDecision.getTrace();
		if (printTrace) {
			prettyLog("New Decision (trace) : ", trace);
		}
		if (printJsonReport || printTextReport) {
			var jsonReport = ReportBuilderUtil.reduceTraceToReport(trace);
			if (printJsonReport) {
				prettyLog("New Decision (report): ", ReportBuilderUtil.reduceTraceToReport(trace));
			}
			if (printTextReport) {
				multiLineLog(ReportTextRenderUtil.textReport(jsonReport, prettyPrint, mapper));
			}
		}
		return tracedDecision;
	}

	private void prettyLog(String prefix, JsonNode json) {
		multiLineLog(
				prefix + (prettyPrint ? "\n" : "") + ReportTextRenderUtil.prettyPrintJson(json, prettyPrint, mapper));
	}

	private void multiLineLog(String message) {
		for (var line : message.replace("\r", "").split("\n"))
			log.info(line);
	}

}
