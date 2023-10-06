package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.hamcrest.HasObligation;
import io.sapl.hamcrest.IsDecision;
import io.sapl.hamcrest.Matchers;
import io.sapl.test.Helper;
import io.sapl.test.grammar.sAPLTest.*;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class ExpectInterpreterTest {

    private ValInterpreter valInterpreterMock;
    private AuthorizationDecisionInterpreter authorizationDecisionInterpreterMock;
    private AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreterMock;
    private ExpectOrVerifyStep expectOrVerifyStepMock;
    private ExpectInterpreter expectInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        authorizationDecisionInterpreterMock = mock(AuthorizationDecisionInterpreter.class);
        authorizationDecisionMatcherInterpreterMock = mock(AuthorizationDecisionMatcherInterpreter.class);
        expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);
        expectInterpreter = new ExpectInterpreter(valInterpreterMock, authorizationDecisionInterpreterMock, authorizationDecisionMatcherInterpreterMock);
    }

    @Nested
    @DisplayName("Single expect")
    class SingleExpectTest {
        @Test
        void interpretSingleExpect_callsAuthorizationDecisionInterpreter_returnsVerifyStep() {
            final var obligationValueMock = mock(Value.class);
            final var resourceValueMock = mock(Value.class);
            final var singleExpectMock = mock(SingleExpect.class);

            final var obligationValues = Helper.mockEList(List.of(obligationValueMock));
            when(singleExpectMock.getObligations()).thenReturn(obligationValues);
            when(singleExpectMock.getResource()).thenReturn(resourceValueMock);

            when(singleExpectMock.getDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT);

            final var authorizationDecisionMock = mock(AuthorizationDecision.class);

            when(authorizationDecisionInterpreterMock.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, resourceValueMock, obligationValues, null)).thenReturn(authorizationDecisionMock);

            final var verifyStepMock = mock(VerifyStep.class);
            when(expectOrVerifyStepMock.expect(authorizationDecisionMock)).thenReturn(verifyStepMock);

            final var result = expectInterpreter.interpretSingleExpect(expectOrVerifyStepMock, singleExpectMock);

            assertEquals(verifyStepMock, result);
        }
    }

    @Nested
    @DisplayName("Repeated expect")
    class RepeatedExpectTest {

        @Nested
        @DisplayName("error cases")
        class ErrorCasesTest {
            @Test
            void interpretRepeatedExpect_doesNothingForExpectStepsBeingNull_returnsInitialVerifyStep() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(null);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }

            @Test
            void interpretRepeatedExpect_doesNothingForExpectStepsBeingEmpty_returnsInitialVerifyStep() {
                final var repeatedExpectStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of());
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(repeatedExpectStepsMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }

            @Test
            void interpretRepeatedExpect_doesNothingForUnknownExpectStep_returnsInitialVerifyStep() {
                final var unknownExpectOrAdjustmentStepMock = mock(ExpectOrAdjustmentStep.class);
                final var repeatedExpectStepsMock = Helper.mockEList(List.of(unknownExpectOrAdjustmentStepMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(repeatedExpectStepsMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }
        }

        @Nested
        @DisplayName("expect next")
        class ExpectNextTest {
            @Test
            void interpretRepeatedExpect_constructsNextPermitWithUnknownAmount_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var numericAmountMock = mock(NumericAmount.class);
                when(nextMock.getAmount()).thenReturn(numericAmountMock);

                when(nextMock.getExpectedDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT);

                when(expectOrVerifyStepMock.expectNextPermit(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextDenyWithNumericAmountBeingMultiple_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var multipleMock = mock(Multiple.class);
                when(nextMock.getAmount()).thenReturn(multipleMock);
                when(multipleMock.getAmount()).thenReturn(BigDecimal.valueOf(3));

                when(nextMock.getExpectedDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY);

                when(expectOrVerifyStepMock.expectNextDeny(3)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextIndeterminateWithNumericAmountBeingOnce_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var onceMock = mock(Once.class);
                when(nextMock.getAmount()).thenReturn(onceMock);

                when(nextMock.getExpectedDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE);

                when(expectOrVerifyStepMock.expectNextIndeterminate(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingOnce_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var onceMock = mock(Once.class);
                when(nextMock.getAmount()).thenReturn(onceMock);

                when(nextMock.getExpectedDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE);

                when(expectOrVerifyStepMock.expectNextNotApplicable(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingMultiple_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var multipleMock = mock(Multiple.class);
                when(nextMock.getAmount()).thenReturn(multipleMock);
                when(multipleMock.getAmount()).thenReturn(BigDecimal.valueOf(5));

                when(nextMock.getExpectedDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE);

                when(expectOrVerifyStepMock.expectNextNotApplicable(5)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("expect next with matcher")
        class ExpectNextWithMatcherTest {
            private MockedStatic<Matchers> saplMatchersMockedStatic;
            private MockedStatic<org.hamcrest.Matchers> hamcrestMatchersMockedStatic;

            @BeforeEach
            void setUp() {
                saplMatchersMockedStatic = mockStatic(Matchers.class, Answers.RETURNS_SMART_NULLS);
                hamcrestMatchersMockedStatic = mockStatic(org.hamcrest.Matchers.class, Answers.RETURNS_SMART_NULLS);
            }

            @AfterEach
            void tearDown() {
                saplMatchersMockedStatic.close();
                hamcrestMatchersMockedStatic.close();
            }

            @Test
            void interpretRepeatedExpect_doesNothingForNullMatchers_returnsInitialVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                when(nextWithMatcherMock.getMatcher()).thenReturn(null);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }

            @Test
            void interpretRepeatedExpect_doesNothingForEmptyMatchers_returnsInitialVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of());
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }

            @Test
            void interpretRepeatedExpect_doesNothingForEmptyMatcherArrayAfterMapping_returnsInitialVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var expectMatcherMock = mock(AuthorizationDecisionMatcher.class);
                final var matchersMock = Helper.mockEList(List.of(expectMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
                verifyNoInteractions(expectOrVerifyStepMock);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleAuthorizationDecisionMatcherWithDecisionPermit_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var authorizationDecisionMatcherMock = mock(io.sapl.test.grammar.sAPLTest.IsDecision.class);
                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of(authorizationDecisionMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                when(authorizationDecisionMatcherMock.getDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT);

                final var isPermitMock = mock(IsDecision.class);
                saplMatchersMockedStatic.when(Matchers::isPermit).thenReturn(isPermitMock);

                when(expectOrVerifyStepMock.expectNext(isPermitMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleAuthorizationDecisionMatcherWithDecisionDeny_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var authorizationDecisionMatcherMock = mock(io.sapl.test.grammar.sAPLTest.IsDecision.class);
                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of(authorizationDecisionMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                when(authorizationDecisionMatcherMock.getDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY);

                final var isDenyMock = mock(IsDecision.class);
                saplMatchersMockedStatic.when(Matchers::isDeny).thenReturn(isDenyMock);

                when(expectOrVerifyStepMock.expectNext(isDenyMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleAuthorizationDecisionMatcherWithDecisionIndeterminate_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var authorizationDecisionMatcherMock = mock(io.sapl.test.grammar.sAPLTest.IsDecision.class);
                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of(authorizationDecisionMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                when(authorizationDecisionMatcherMock.getDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE);

                final var isIndeterminateMock = mock(IsDecision.class);
                saplMatchersMockedStatic.when(Matchers::isIndeterminate).thenReturn(isIndeterminateMock);

                when(expectOrVerifyStepMock.expectNext(isIndeterminateMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleAuthorizationDecisionMatcherWithDecisionNotApplicable_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var authorizationDecisionMatcherMock = mock(io.sapl.test.grammar.sAPLTest.IsDecision.class);
                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of(authorizationDecisionMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                when(authorizationDecisionMatcherMock.getDecision()).thenReturn(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE);

                final var isNotApplicableMock = mock(IsDecision.class);
                saplMatchersMockedStatic.when(Matchers::isNotApplicable).thenReturn(isNotApplicableMock);

                when(expectOrVerifyStepMock.expectNext(isNotApplicableMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }


            @Test
            void interpretRepeatedExpect_correctlyMapsSingleObligationMatcher_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var authorizationDecisionMatcherMock = mock(io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcher.class);
                final var matchersMock = Helper.mockEList(List.of(authorizationDecisionMatcherMock));
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                final var mappedAuthorizationDecisionMatcherMock = mock(Matcher.class);
                when(authorizationDecisionMatcherInterpreterMock.getMatcherFromExpectMatcher(authorizationDecisionMatcherMock)).thenReturn(mappedAuthorizationDecisionMatcherMock);

                when(expectOrVerifyStepMock.expectNext(mappedAuthorizationDecisionMatcherMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsMultipleMixedMatchersAndFiltersNullResults_returnsAdjustedVerifyStep() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);
                final var invalidMatcherMock = mock(AuthorizationDecisionMatcher.class);
                final var isDecisionMock = mock(io.sapl.test.grammar.sAPLTest.IsDecision.class);

                final var hasObligationOrAdivceMappedMock = mock(Matcher.class);
                final var isDecisionMappedMock = mock(Matcher.class);
                when(authorizationDecisionMatcherInterpreterMock.getMatcherFromExpectMatcher(hasObligationOrAdviceMock)).thenReturn(hasObligationOrAdivceMappedMock);
                when(authorizationDecisionMatcherInterpreterMock.getMatcherFromExpectMatcher(invalidMatcherMock)).thenReturn(null);
                when(authorizationDecisionMatcherInterpreterMock.getMatcherFromExpectMatcher(isDecisionMock)).thenReturn(isDecisionMappedMock);

                final var allOfMock = mock(Matcher.class);
                hamcrestMatchersMockedStatic.when(() -> org.hamcrest.Matchers.allOf(new Matcher[] {hasObligationOrAdivceMappedMock, isDecisionMappedMock})).thenReturn(allOfMock);

                when(expectOrVerifyStepMock.expectNext(allOfMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("attribute adjustment")
        class AttributeAdjustmentTest {
            @Test
            void interpretRepeatedExpect_interpretsAttributeAdjustment_returnsAdjustedVerifyStep() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                final var attributeAdjustmentMock = mock(AttributeAdjustment.class);
                final var eListMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(attributeAdjustmentMock));
                when(repeatedExpectMock.getExpectSteps()).thenReturn(eListMock);

                final var valMock = mock(Value.class);
                when(attributeAdjustmentMock.getReturnValue()).thenReturn(valMock);
                final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
                when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);

                when(attributeAdjustmentMock.getAttribute()).thenReturn("foo");
                when(expectOrVerifyStepMock.thenAttribute("foo", saplValMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("await")
        class AwaitTest {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                final var awaitMock = mock(Await.class);
                final var eListMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(awaitMock));
                when(repeatedExpectMock.getExpectSteps()).thenReturn(eListMock);

                final var temporalAmountMock = mock(TemporalAmount.class);
                when(awaitMock.getDuration()).thenReturn(temporalAmountMock);

                when(temporalAmountMock.getSeconds()).thenReturn(BigDecimal.valueOf(5));

                when(expectOrVerifyStepMock.thenAwait(Duration.ofSeconds(5))).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("no event")
        class NoEventTest {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                final var noEventMock = mock(NoEvent.class);
                final var eListMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(noEventMock));
                when(repeatedExpectMock.getExpectSteps()).thenReturn(eListMock);

                final var temporalAmountMock = mock(TemporalAmount.class);
                when(noEventMock.getDuration()).thenReturn(temporalAmountMock);

                when(temporalAmountMock.getSeconds()).thenReturn(BigDecimal.valueOf(3));

                when(expectOrVerifyStepMock.expectNoEvent(Duration.ofSeconds(3))).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }
    }
}