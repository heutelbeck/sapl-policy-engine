package io.sapl.api.pdp.multirequest;

import java.util.HashMap;
import java.util.Map;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;

public class MultiDecision {

    private Map<String, Decision> decisionsByRequestId = new HashMap<>();

    public MultiDecision(MultiResponse multiResponse) {
        for (Map.Entry<String, Response> entry : multiResponse.getResponses().entrySet()) {
            decisionsByRequestId.put(entry.getKey(), entry.getValue().getDecision());
        }
    }

    public Decision getDecisionForRequest(String requestId) {
        return decisionsByRequestId.get(requestId);
    }

    public boolean getFlagForRequest(String requestId) {
        return decisionsByRequestId.get(requestId) == Decision.PERMIT;
    }
}
