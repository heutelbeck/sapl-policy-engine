package io.sapl.interpreter;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

public class PDPDecision implements Traced {
	CombinedDecision          combinedDecision;
	AuthorizationSubscription authzSubscription;
	Instant                   timestamp;

	@Override
	public String evaluationTree() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public String report() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public JsonNode jsonReport() {
		// TODO Auto-generated method stub
		return Val.JSON.objectNode();
	}
}
