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

    AuthorizationDecision constructAuthorizationDecision(final io.sapl.test.grammar.sAPLTest.AuthorizationDecision decision, final List<JsonElement> obligationElements, final List<JsonElement> resourceElements) {
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

    private AuthorizationDecision getAuthorizationDecisionFromDSL(final io.sapl.test.grammar.sAPLTest.AuthorizationDecision decision) {
        return switch (decision) {
            case PERMIT -> AuthorizationDecision.PERMIT;
            case DENY -> AuthorizationDecision.DENY;
            case INDETERMINATE -> AuthorizationDecision.INDETERMINATE;
            case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
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
