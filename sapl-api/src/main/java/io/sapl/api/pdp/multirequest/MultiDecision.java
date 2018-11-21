package io.sapl.api.pdp.multirequest;

import java.util.HashMap;
import java.util.Map;

import io.sapl.api.pdp.Decision;

public class MultiDecision {

    private Map<String, Decision> decisionsByRequestId;

    public MultiDecision(MultiResponse multiResponse) {
        decisionsByRequestId = new HashMap<>(multiResponse.size());
        multiResponse.forEach(identifiableResponse -> {
            final String requestId = identifiableResponse.getRequestId();
            final Decision decision = identifiableResponse.getResponse().getDecision();
            decisionsByRequestId.put(requestId, decision);
        });
    }

    public Decision getDecisionForRequestWithId(String requestId) {
        return decisionsByRequestId.get(requestId);
    }

    public boolean getFlagForRequestWithId(String requestId) {
        return decisionsByRequestId.get(requestId) == Decision.PERMIT;
    }
}
