package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionType;
import io.sapl.test.grammar.sAPLTest.Value;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AuthorizationDecisionInterpreter {

    private final ValInterpreter valInterpreter;
    private final ObjectMapper objectMapper;

    AuthorizationDecision constructAuthorizationDecision(final AuthorizationDecisionType decisionType, final Value resource, final List<Value> obligations, final List<Value> advice) {
        if (decisionType == null) {
            throw new SaplTestException("AuthorizationDecisionType is null");
        }

        var authorizationDecision = getAuthorizationDecisionFromDSL(decisionType);

        if (resource != null) {
            final var mappedResource = valInterpreter.getValFromValue(resource);
            authorizationDecision = authorizationDecision.withResource(mappedResource.get());
        }

        final var obligationArray = getMappedValArrayFromValues(obligations);

        if (obligationArray != null) {
            authorizationDecision = authorizationDecision.withObligations(obligationArray);
        }

        final var adviceArray = getMappedValArrayFromValues(advice);

        if (adviceArray != null) {
            authorizationDecision = authorizationDecision.withAdvice(adviceArray);
        }

        return authorizationDecision;
    }

    private ArrayNode getMappedValArrayFromValues(final List<Value> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        final var valArray = objectMapper.createArrayNode();

        values.stream()
                .map(valInterpreter::getValFromValue)
                .map(io.sapl.api.interpreter.Val::get)
                .forEach(valArray::add);

        return valArray;
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSL(final AuthorizationDecisionType decision) {
        return switch (decision) {
            case PERMIT -> AuthorizationDecision.PERMIT;
            case DENY -> AuthorizationDecision.DENY;
            case INDETERMINATE -> AuthorizationDecision.INDETERMINATE;
            case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }
}
