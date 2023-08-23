package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscriptionElement;
import io.sapl.test.grammar.sAPLTest.JsonElement;
import io.sapl.test.grammar.sAPLTest.Plain;
import io.sapl.test.grammar.sAPLTest.Structured;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class ExpectStepBuilderDefaultImplTest {

    private ExpectStepBuilderDefaultImpl expectStepBuilderDefaultImpl;

    private WhenStep whenStepMock;

    private AuthorizationSubscription authorizationSubscriptionMock;

    private TestCase testCaseMock;

    private ExpectStep expectStepMock;

    @BeforeEach
    void setUp() {
        whenStepMock = mock(WhenStep.class);
        authorizationSubscriptionMock = mock(AuthorizationSubscription.class);
        testCaseMock = mock(TestCase.class, Answers.RETURNS_DEEP_STUBS);
        expectStepMock = mock(ExpectStep.class);

        when(testCaseMock.getWhenStep().getAuthorizationSubscription()).thenReturn(authorizationSubscriptionMock);

        expectStepBuilderDefaultImpl = new ExpectStepBuilderDefaultImpl(new ObjectMapper());
    }

    @Test
    void constructExpectStep_forPlainValues_returnsCorrectExpectStep() {
        final var subjectMock = mock(Plain.class);
        final var actionMock = mock(Plain.class);
        final var resourceMock = mock(Plain.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        when(subjectMock.getValue()).thenReturn("plainSubject");
        when(actionMock.getValue()).thenReturn("plainAction");
        when(resourceMock.getValue()).thenReturn("plainResource");

        when(whenStepMock.when(any(io.sapl.api.pdp.AuthorizationSubscription.class))).thenAnswer(invocationOnMock -> {

            final var authorizationSubscription = (io.sapl.api.pdp.AuthorizationSubscription) invocationOnMock.getArgument(0);

            assertEquals("plainSubject", authorizationSubscription.getSubject().asText());
            assertEquals("plainAction", authorizationSubscription.getAction().asText());
            assertEquals("plainResource", authorizationSubscription.getResource().asText());

            return expectStepMock;
        });

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }

    @Test
    void constructExpectStep_forStructuredValues_returnsCorrectExpectStep() {
        final var subjectMock = mock(Structured.class);
        final var actionMock = mock(Structured.class);
        final var resourceMock = mock(Structured.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        final var jsonElementMock = mock(JsonElement.class);
        when(jsonElementMock.getKey()).thenReturn("key");
        when(jsonElementMock.getValue()).thenReturn("value");
        final var eListMock = mock(EList.class, delegatesTo(List.of(jsonElementMock)));

        when(subjectMock.getElements()).thenReturn(null);
        when(actionMock.getElements()).thenReturn(eListMock);
        when(resourceMock.getElements()).thenReturn(null);

        when(whenStepMock.when(any(io.sapl.api.pdp.AuthorizationSubscription.class))).thenAnswer(invocationOnMock -> {

            final var authorizationSubscription = (io.sapl.api.pdp.AuthorizationSubscription) invocationOnMock.getArgument(0);

            assertTrue(authorizationSubscription.getSubject().isEmpty());
            assertEquals("{\"key\":\"value\"}", authorizationSubscription.getAction().toString());
            assertTrue(authorizationSubscription.getResource().isEmpty());

            return expectStepMock;
        });

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }

    @Test
    void constructExpectStep_forPlainAndStructuredValues_returnsCorrectExpectStep() {
        final var subjectMock = mock(Plain.class);
        final var actionMock = mock(Structured.class);
        final var resourceMock = mock(Plain.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        final var jsonElementMock = mock(JsonElement.class);
        when(jsonElementMock.getKey()).thenReturn("key");
        when(jsonElementMock.getValue()).thenReturn("value");
        final var eListMock = mock(EList.class, delegatesTo(List.of(jsonElementMock)));

        when(subjectMock.getValue()).thenReturn("plainSubject");
        when(actionMock.getElements()).thenReturn(eListMock);
        when(resourceMock.getValue()).thenReturn("plainResource");

        when(whenStepMock.when(any(io.sapl.api.pdp.AuthorizationSubscription.class))).thenAnswer(invocationOnMock -> {

            final var authorizationSubscription = (io.sapl.api.pdp.AuthorizationSubscription) invocationOnMock.getArgument(0);

            assertEquals("plainSubject", authorizationSubscription.getSubject().asText());
            assertEquals("{\"key\":\"value\"}", authorizationSubscription.getAction().toString());
            assertEquals("plainResource", authorizationSubscription.getResource().asText());

            return expectStepMock;
        });

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }

    @Test
    void constructExpectStep_nullPlainValueEvaluatesToEmptyStringAndNullStructuredValueElementsEvaluatesToEmptyObjectNode_returnsCorrectExpectStep() {
        final var subjectMock = mock(Plain.class);
        final var actionMock = mock(Structured.class);
        final var resourceMock = mock(Plain.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        when(subjectMock.getValue()).thenReturn(null);
        when(actionMock.getElements()).thenReturn(null);
        when(resourceMock.getValue()).thenReturn(null);

        when(whenStepMock.when(any(io.sapl.api.pdp.AuthorizationSubscription.class))).thenAnswer(invocationOnMock -> {

            final var authorizationSubscription = (io.sapl.api.pdp.AuthorizationSubscription) invocationOnMock.getArgument(0);

            assertEquals("", authorizationSubscription.getSubject().asText());
            assertTrue(authorizationSubscription.getAction().isEmpty());
            assertEquals("", authorizationSubscription.getResource().asText());

            return expectStepMock;
        });

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }

    @Test
    void constructExpectStep_unexpectedAuthorizationSubscriptionElementEvaluatesToNull_returnsCorrectExpectStep() {
        final var subjectMock = mock(AuthorizationSubscriptionElement.class);
        final var actionMock = mock(AuthorizationSubscriptionElement.class);
        final var resourceMock = mock(AuthorizationSubscriptionElement.class);

        when(authorizationSubscriptionMock.getSubject()).thenReturn(subjectMock);
        when(authorizationSubscriptionMock.getAction()).thenReturn(actionMock);
        when(authorizationSubscriptionMock.getResource()).thenReturn(resourceMock);

        when(whenStepMock.when(any(io.sapl.api.pdp.AuthorizationSubscription.class))).thenAnswer(invocationOnMock -> {

            final var authorizationSubscription = (io.sapl.api.pdp.AuthorizationSubscription) invocationOnMock.getArgument(0);

            assertTrue(authorizationSubscription.getSubject().isNull());
            assertTrue(authorizationSubscription.getAction().isNull());
            assertTrue(authorizationSubscription.getResource().isNull());

            return expectStepMock;
        });

        final var result = expectStepBuilderDefaultImpl.constructExpectStep(testCaseMock, whenStepMock);
        assertEquals(expectStepMock, result);
    }
}