package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ExpectStepBuilderDefaultImplTest {

    MockedStatic<io.sapl.api.pdp.AuthorizationSubscription> authorizationSubscriptionMockedStatic;
    private ValInterpreter valInterpreterMock;
    private ExpectStepBuilderDefaultImpl expectStepBuilderDefaultImpl;

    @BeforeEach
    void setUp() {
        authorizationSubscriptionMockedStatic = mockStatic(io.sapl.api.pdp.AuthorizationSubscription.class);

        valInterpreterMock = mock(ValInterpreter.class);

        expectStepBuilderDefaultImpl = new ExpectStepBuilderDefaultImpl(valInterpreterMock);
    }

    @AfterEach
    void tearDown() {
        authorizationSubscriptionMockedStatic.close();
    }

    @Test
    void constructExpectStep_returnsCorrectExpectStep() {
        final var testCaseMock = mock(TestCase.class);

        final var saplTestWhenStepMock = mock(io.sapl.test.grammar.sAPLTest.WhenStep.class);
        when(testCaseMock.getWhenStep()).thenReturn(saplTestWhenStepMock);

        final var authorizationSubscriptionMock = mock(AuthorizationSubscription.class);
        when(saplTestWhenStepMock.getAuthorizationSubscription()).thenReturn(authorizationSubscriptionMock);

        final var subjectMock = mock(Value.class);
        final var actionMock = mock(Value.class);
        final var resourceMock = mock(Value.class);

        final var saplSubjectMock = mock(Val.class);
        final var saplActionMock = mock(Val.class);
        final var saplResourceMock = mock(Val.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        when(valInterpreterMock.getValFromReturnValue(subjectMock)).thenReturn(saplSubjectMock);
        when(valInterpreterMock.getValFromReturnValue(actionMock)).thenReturn(saplActionMock);
        when(valInterpreterMock.getValFromReturnValue(resourceMock)).thenReturn(saplResourceMock);

        final var subjectJsonNodeMock = mock(JsonNode.class);
        final var actionJsonNodeMock = mock(JsonNode.class);
        final var resourceJsonNodeMock = mock(JsonNode.class);

        when(saplSubjectMock.get()).thenReturn(subjectJsonNodeMock);
        when(saplActionMock.get()).thenReturn(actionJsonNodeMock);
        when(saplResourceMock.get()).thenReturn(resourceJsonNodeMock);

        final var saplAuthorizationSubscriptionMock = mock(io.sapl.api.pdp.AuthorizationSubscription.class);
        authorizationSubscriptionMockedStatic.when(() -> io.sapl.api.pdp.AuthorizationSubscription.of(subjectJsonNodeMock, actionJsonNodeMock, resourceJsonNodeMock)).thenReturn(saplAuthorizationSubscriptionMock);

        final var whenStepMock = mock(WhenStep.class);
        final var expectStepMock = mock(ExpectStep.class);
        when(whenStepMock.when(saplAuthorizationSubscriptionMock)).thenReturn(expectStepMock);

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }
}