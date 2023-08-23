package io.sapl.test.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscriptionElement;
import io.sapl.test.grammar.sAPLTest.Plain;
import io.sapl.test.grammar.sAPLTest.Structured;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ExpectStepBuilderDefaultImpl implements ExpectStepBuilder {

    private final ObjectMapper objectMapper;

    @Override
    public ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep) {
        final var authorizationSubscription = getAuthorizationSubscriptionFromDSL(testCase.getWhenStep().getAuthorizationSubscription());
        return whenStep.when(authorizationSubscription);
    }

    private io.sapl.api.pdp.AuthorizationSubscription getAuthorizationSubscriptionFromDSL(AuthorizationSubscription authorizationSubscription) {
        final var subject = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getSubject());
        final var action = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getAction());
        final var resource = getValueFromAuthorizationSubscriptionElement(authorizationSubscription.getResource());

        return io.sapl.api.pdp.AuthorizationSubscription.of(subject, action, resource);
    }

    private Object getValueFromAuthorizationSubscriptionElement(AuthorizationSubscriptionElement authorizationSubscriptionElement) {
        if (authorizationSubscriptionElement instanceof Plain plainValue) {
            return Optional.ofNullable(plainValue.getValue()).orElse("");
        } else if (authorizationSubscriptionElement instanceof Structured structuredValue) {
            final var structuredValueElements = structuredValue.getElements();
            ObjectNode structuredValueObject = objectMapper.createObjectNode();
            if (structuredValueElements != null) {
                structuredValueElements.forEach(obligationElement -> structuredValueObject.put(obligationElement.getKey(), obligationElement.getValue()));
            }
            return structuredValueObject;
        }
        return null;
    }
}
