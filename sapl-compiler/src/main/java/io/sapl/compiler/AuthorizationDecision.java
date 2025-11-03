package io.sapl.compiler;

import io.sapl.api.pdp.Decision;
import io.sapl.api.v2.Value;

import java.util.List;

public record AuthorizationDecision(Decision decision, Value resource, List<Value> obligations, List<Value> advice) {

}
