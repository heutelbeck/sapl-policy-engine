package io.sapl.test.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.Value;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationDecisionInterpreter {

    private final ValInterpreter valInterpreter;

    private final ObjectMapper objectMapper;

    AuthorizationDecision constructAuthorizationDecision(final io.sapl.test.grammar.sAPLTest.AuthorizationDecision decision, final Value obligation, final Value resource) {
        var authorizationDecision = getAuthorizationDecisionFromDSL(decision);


        final var mappedObligation = valInterpreter.getValFromReturnValue(obligation);

        if (mappedObligation != null) {
            final var obligations = objectMapper.createArrayNode();
            obligations.add(mappedObligation.get());
            authorizationDecision = authorizationDecision.withObligations(obligations);
        }

        final var mappedResource = valInterpreter.getValFromReturnValue(resource);

        if (mappedResource != null) {
            authorizationDecision = authorizationDecision.withResource(mappedResource.get());
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
}
