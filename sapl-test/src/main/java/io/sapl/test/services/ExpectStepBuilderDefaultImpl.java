package io.sapl.test.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscriptionElement;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscriptionObject;
import io.sapl.test.grammar.sAPLTest.Plain;
import io.sapl.test.grammar.sAPLTest.Structured;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;

public final class ExpectStepBuilderDefaultImpl implements ExpectStepBuilder {

    @Override
    public ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep) {
        if (testCase.getWhenStep().getAuthorizationSubscription() instanceof AuthorizationSubscriptionObject authorizationSubscription) {
            return whenStep.when(getAuthorizationSubscriptionFromDSL(authorizationSubscription));
        }
        return null;
    }

    private AuthorizationSubscription getAuthorizationSubscriptionFromDSL(AuthorizationSubscriptionObject authorizationSubscription) {
        final var subject = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getSubject());
        final var action = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getAction());
        final var resource = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getResource());

        return AuthorizationSubscription.of(subject, action, resource);
    }

    private Object getValueFromAuthorizationSubscriptionElement(AuthorizationSubscriptionElement authorizationSubscriptionElement) {
        if(authorizationSubscriptionElement instanceof Plain plainValue) {
            return plainValue.getValue();
        } else if(authorizationSubscriptionElement instanceof Structured structuredValue) {
            final var structuredValueElements = structuredValue.getElements();
            if(structuredValueElements != null && !structuredValueElements.isEmpty()) {
                final var mapper = new ObjectMapper();
                ObjectNode structuredValueObject = mapper.createObjectNode();
                structuredValueElements.forEach(obligationElement -> structuredValueObject.put(obligationElement.getKey(), obligationElement.getValue()));
                return structuredValueObject;
            }
        }
        return null;
    }
}
