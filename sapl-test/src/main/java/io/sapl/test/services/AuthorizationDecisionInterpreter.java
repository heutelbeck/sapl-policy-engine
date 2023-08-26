package io.sapl.test.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.JsonElement;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationDecisionInterpreter {

    private final ObjectMapper objectMapper;

    AuthorizationDecision constructAuthorizationDecision(final String decision, final List<JsonElement> obligationElements, final List<JsonElement> resourceElements) {
        var authorizationDecision = getAuthorizationDecisionFromDSL(decision);

        final var obligations = getObligations(obligationElements);

        if (obligations != null) {
            authorizationDecision = authorizationDecision.withObligations(obligations);
        }

        final var resource = getResource(resourceElements);

        if (resource != null) {
            authorizationDecision = authorizationDecision.withResource(resource);
        }

        return authorizationDecision;
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSL(final String decision) {
        return switch (decision) {
            case "permit" -> AuthorizationDecision.PERMIT;
            case "deny" -> AuthorizationDecision.DENY;
            case "indeterminate" -> AuthorizationDecision.INDETERMINATE;
            default -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }

    private ArrayNode getObligations(final List<JsonElement> obligationElements) {
        if (obligationElements != null && !obligationElements.isEmpty()) {
            final var obligations = objectMapper.createArrayNode();
            //TODO support multiple obligations
            final var obligation = objectMapper.createObjectNode();
            obligationElements.forEach(obligationElement -> obligation.put(obligationElement.getKey(), obligationElement.getValue()));
            obligations.add(obligation);
            return obligations;
        }
        return null;
    }

    private ObjectNode getResource(final List<JsonElement> resourceElements) {
        if (resourceElements == null || resourceElements.isEmpty()) {
            return null;
        }

        final var resource = objectMapper.createObjectNode();
        resourceElements.forEach(resourceElement -> resource.put(resourceElement.getKey(), resourceElement.getValue()));
        return resource;
    }
}
