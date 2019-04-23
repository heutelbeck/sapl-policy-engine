package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.sapl.api.pdp.Decision;
import lombok.Value;

@Value
public class MultiDecision {

    @JsonInclude(NON_EMPTY)
    private Map<String, Decision> decisionsByRequestId;

    public MultiDecision() {
        decisionsByRequestId = new HashMap<>();
    }

    public void setDecisionForRequestWithId(String requestId, Decision decision) {
        requireNonNull(requestId, "requestId must not be null");
        requireNonNull(decision, "decision must not be null");
        decisionsByRequestId.put(requestId, decision);
    }

    public Decision getDecisionForRequestWithId(String requestId) {
        return decisionsByRequestId.get(requestId);
    }

    public boolean isAccessPermittedForRequestWithId(String requestId) {
        return decisionsByRequestId.get(requestId) == Decision.PERMIT;
    }
}
