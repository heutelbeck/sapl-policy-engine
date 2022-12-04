package io.sapl.interpreter.trace;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.Data;

@Data
public class DocumentEvaluationTrace {
	String                   name;
	String                   type;
	AuthorizationDecision    decision;
	Queue<CapturedAttribute> attributes = new ConcurrentLinkedQueue<>();
}
