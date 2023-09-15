package io.sapl.test.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.Value;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationDecisionInterpreter {

    private final ValInterpreter valInterpreter;

    private final ObjectMapper objectMapper;

    AuthorizationDecision constructAuthorizationDecision(final io.sapl.test.grammar.sAPLTest.AuthorizationDecision decision, final Value resource, final List<Value> obligations, final List<Value> advice) {
        var authorizationDecision = getAuthorizationDecisionFromDSL(decision);

        final var mappedResource = valInterpreter.getValFromReturnValue(resource);

        if (mappedResource != null) {
            authorizationDecision = authorizationDecision.withResource(mappedResource.get());
        }

        final var obligationArray = getMappedValArrayFromValues(obligations);

        if(obligationArray != null) {
            authorizationDecision = authorizationDecision.withObligations(obligationArray);
        }

        final var adviceArray = getMappedValArrayFromValues(advice);

        if(adviceArray != null) {
            authorizationDecision = authorizationDecision.withAdvice(adviceArray);
        }

        return authorizationDecision;
    }

    private ArrayNode getMappedValArrayFromValues(final List<Value> values) {
        if(values == null || values.isEmpty()) {
            return null;
        }

        final var valArray = objectMapper.createArrayNode();

        values.stream()
              .map(valInterpreter::getValFromReturnValue)
              .filter(Objects::nonNull)
              .map(io.sapl.api.interpreter.Val::get)
              .forEach(valArray::add);

        return valArray;
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
