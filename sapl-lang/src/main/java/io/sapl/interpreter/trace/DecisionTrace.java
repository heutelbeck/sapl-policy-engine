package io.sapl.interpreter.trace;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class DecisionTrace {
	private String                               clientId;
	private String                               correlationId;
	private AuthorizationSubscription            subscription;
	private String                               combiningAlgorithm;
	private Map<String, DocumentEvaluationTrace> documentTraces = new ConcurrentHashMap<>();
	private AuthorizationDecision                decision;
	private Instant                              decisionTime;

	@Override
	public String toString() {
		var trace = "Decision Trace --------------------------\n";
		trace += "Subscription  : " + subscription + "\n";
		trace += "Decision      : " + decision + "\n";
		trace += "Context Information ---------------------\n";
		trace += "CorrelationId : " + correlationId + "\n";
		trace += "Client        : " + clientId + "\n";
		trace += "Timestamp     : " + decisionTime + "\n";
		trace += "Combining Alg.: " + combiningAlgorithm + "\n";
		trace += "Explanation -----------------------------\n";
		trace += "Matching Documents: \n";
		for (var documentTrace : documentTraces.values()) {
			trace += "  - Document      : (" + documentTrace.getType() + ") \"" + documentTrace.getName() + "\"\n";
			trace += "    Decision      : " + documentTrace.getDecision() + "\n";
			var attributes = documentTrace.getAttributes();
			if (!attributes.isEmpty()) {
				trace += "    Attributes    :\n";
			}
			for (var attribute : attributes) {
				trace += "                   - " + attribute + "\n";
			}
		}
		trace += "-----------------------------------------";
		return trace;
	}

	public void log() {
		log("INFO");
	}

	public void log(String level) {
		var logger = loggerForLevel(level);
		Arrays.stream(toString().split("\n")).forEach(logger);
	}

	private Consumer<String> loggerForLevel(String level) {
		var uppercaseLevel = level.toUpperCase();
		if (uppercaseLevel.equals("SOUT"))
			return s -> System.out.println(s);
		if (uppercaseLevel.equals("ERROR"))
			return s -> log.error(s);
		if (uppercaseLevel.equals("DEBUG"))
			return s -> log.debug(s);
		if (uppercaseLevel.equals("WARN"))
			return s -> log.warn(s);
		if (uppercaseLevel.equals("TRACE"))
			return s -> log.trace(s);
		return s -> log.info(s);
	}
}
