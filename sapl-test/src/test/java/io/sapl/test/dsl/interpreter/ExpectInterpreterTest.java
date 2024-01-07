/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.test.dsl.interpreter;

import static io.sapl.test.dsl.ParserUtil.compareArgumentToStringLiteral;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.hamcrest.Matchers;
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AnyDecision;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionType;
import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.ExpectOrAdjustmentStep;
import io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice;
import io.sapl.test.grammar.sAPLTest.IsDecision;
import io.sapl.test.grammar.sAPLTest.Next;
import io.sapl.test.grammar.sAPLTest.NextWithDecision;
import io.sapl.test.grammar.sAPLTest.NextWithMatcher;
import io.sapl.test.grammar.sAPLTest.NumericAmount;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import java.time.Duration;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpectInterpreterTest {
    @Mock
    private ValInterpreter                          valInterpreterMock;
    @Mock
    private AuthorizationDecisionInterpreter        authorizationDecisionInterpreterMock;
    @Mock
    private AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreterMock;
    @Mock
    private DurationInterpreter                     durationInterpreterMock;
    @Mock
    private MultipleAmountInterpreter               multipleAmountInterpreter;
    @InjectMocks
    private ExpectInterpreter                       expectInterpreter;
    @Mock
    private ExpectOrVerifyStep                      expectOrVerifyStepMock;

    private <T extends ExpectChain> T buildExpectChain(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getExpectChainRule);
    }

    @Nested
    @DisplayName("Single expect")
    class SingleExpectTest {
        @Test
        void interpretSingleExpect_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
            final SingleExpect singleExpect = buildExpectChain("expect single permit");

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpect(null, singleExpect));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullSingleExpect_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpect(expectOrVerifyStepMock, null));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullExpectOrVerifyStepAndNullSingleExpect_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpect(null, null));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullDecisionInSingleExpect_throwsSaplTestException() {
            final var singleExpectMock = mock(SingleExpect.class);

            when(singleExpectMock.getDecision()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpect(expectOrVerifyStepMock, singleExpectMock));

            assertEquals("AuthorizationDecision is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_callsAuthorizationDecisionInterpreter_returnsVerifyStep() {
            final SingleExpect singleExpect = buildExpectChain(
                    "expect single permit with obligations \"obligation\" with resource \"resource\" with advice \"advice1\",\"advice2\"");

            final var authorizationDecisionMock = mock(AuthorizationDecision.class);

            when(authorizationDecisionInterpreterMock.constructAuthorizationDecision(
                    eq(AuthorizationDecisionType.PERMIT), compareArgumentToStringLiteral("resource"), any(), any()))
                    .thenAnswer(invocationOnMock -> {
                        final List<StringLiteral> obligations = invocationOnMock.getArgument(2);
                        final List<StringLiteral> advice = invocationOnMock.getArgument(3);

                        assertEquals(1, obligations.size());
                        assertEquals("obligation", obligations.get(0).getString());
                        assertEquals(2, advice.size());
                        assertEquals("advice1", advice.get(0).getString());
                        assertEquals("advice2", advice.get(1).getString());
                        return authorizationDecisionMock;
                    });

            final var verifyStepMock = mock(VerifyStep.class);
            when(expectOrVerifyStepMock.expect(authorizationDecisionMock)).thenReturn(verifyStepMock);

            final var result = expectInterpreter.interpretSingleExpect(expectOrVerifyStepMock, singleExpect);

            assertEquals(verifyStepMock, result);
        }
    }

    @Nested
    @DisplayName("Single expect with matcher")
    class SingleExpectWithMatcherTest {

        @Test
        void interpretSingleExpectWithMatcher_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
            final SingleExpectWithMatcher singleExpectWithMatcher = buildExpectChain(
                    "expect single decision matching any");

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpectWithMatcher(null, singleExpectWithMatcher));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullSingleExpectWithMatcher_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpectWithMatcher(expectOrVerifyStepMock, null));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullExpectOrVerifyStepAndNullSingleExpectWithMatcher_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectInterpreter.interpretSingleExpectWithMatcher(null, null));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullMatcherInSingleExpectWithMatcher_throwsSaplTestException() {
            final var singleExpectWithMatcherMock = mock(SingleExpectWithMatcher.class);

            when(singleExpectWithMatcherMock.getMatcher()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> expectInterpreter
                    .interpretSingleExpectWithMatcher(expectOrVerifyStepMock, singleExpectWithMatcherMock));

            assertEquals("SingleExpectWithMatcher does not contain a matcher", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_callsAuthorizationDecisionMatcherInterpreter_returnsVerifyStep() {
            final SingleExpectWithMatcher singleExpectWithMatcher = buildExpectChain(
                    "expect single decision matching any");

            final Matcher<AuthorizationDecision> authorizationDecisionMatcherMock = mock(Matcher.class);

            when(authorizationDecisionMatcherInterpreterMock
                    .getHamcrestAuthorizationDecisionMatcher(any(AnyDecision.class)))
                    .thenReturn(authorizationDecisionMatcherMock);

            final var verifyStepMock = mock(VerifyStep.class);
            when(expectOrVerifyStepMock.expect(authorizationDecisionMatcherMock)).thenReturn(verifyStepMock);

            final var result = expectInterpreter.interpretSingleExpectWithMatcher(expectOrVerifyStepMock,
                    singleExpectWithMatcher);

            assertEquals(verifyStepMock, result);
        }
    }

    @Nested
    @DisplayName("Repeated expect")
    class RepeatedExpectTest {

        @Nested
        @DisplayName("Error cases")
        class ErrorCasesTest {
            @Test
            void interpretRepeatedExpect_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect permit once");

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(null, repeatedExpect));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullRepeatedExpect_throwsSaplTestException() {
                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, null));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullExpectOrVerifyStepAndNullRepeatedExpect_throwsSaplTestException() {
                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(null, null));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectStepsBeingNull_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No ExpectOrAdjustmentStep found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectStepsBeingEmpty_returnsInitialVerifyStep() {
                final var repeatedExpectStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of());
                final var repeatedExpectMock      = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(repeatedExpectStepsMock);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No ExpectOrAdjustmentStep found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesUnknownExpectStep_throwsSaplTestException() {
                final var unknownExpectOrAdjustmentStepMock = mock(ExpectOrAdjustmentStep.class);
                final var repeatedExpectStepsMock           = Helper
                        .mockEList(List.of(unknownExpectOrAdjustmentStepMock));
                final var repeatedExpectMock                = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectSteps()).thenReturn(repeatedExpectStepsMock);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of ExpectOrAdjustmentStep", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("Expect next")
        class ExpectNextTest {

            @Test
            void interpretRepeatedExpect_constructsNextPermitWithUnknownAmount_returnsVerifyStepWithNextPermitOnce() {
                final var nextMock                    = mock(Next.class);
                final var expectOrAdjustmentStepsMock = Helper.mockEList(List.<ExpectOrAdjustmentStep>of(nextMock));
                final var repeatedExpectMock          = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var numericAmountMock = mock(NumericAmount.class);
                when(nextMock.getAmount()).thenReturn(numericAmountMock);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of NumericAmount", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_constructsNextDenyWithNumericAmountBeingMultiple_returnsVerifyStepWithNextPermitOnce() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect deny 3x");

                when(multipleAmountInterpreter.getAmountFromMultipleAmountString("3x")).thenReturn(3);

                when(expectOrVerifyStepMock.expectNextDeny(3)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextIndeterminateWithNumericAmountBeingOnce_returnsVerifyStepWithNextPermitOnce() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect indeterminate once");

                when(expectOrVerifyStepMock.expectNextIndeterminate(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingOnce_returnsVerifyStepWithNextPermitOnce() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect permit once");

                when(expectOrVerifyStepMock.expectNextPermit(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingMultiple_returnsVerifyStepWithNextPermitOnce() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect notApplicable 5x");

                when(multipleAmountInterpreter.getAmountFromMultipleAmountString("5x")).thenReturn(5);

                when(expectOrVerifyStepMock.expectNextNotApplicable(5)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Expect next with decision")
        class ExpectNextWithDecisionTest {
            @Test
            void interpretRepeatedExpect_handlesNextWithNullDecision_throwsSaplTestException() {
                final var nextWithDecisionMock        = mock(NextWithDecision.class);
                final var expectOrAdjustmentStepsMock = Helper
                        .mockEList(List.<ExpectOrAdjustmentStep>of(nextWithDecisionMock));
                final var repeatedExpectMock          = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                when(nextWithDecisionMock.getExpectedDecision()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("AuthorizationDecision is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_buildsAuthorizationDecision_returnsVerifyStepWithNextAuthorizationDecision() {
                final RepeatedExpect repeatedExpect = buildExpectChain(
                        "- expect decision permit with obligations \"obligation\" with resource \"resource\" with advice \"advice\"");

                final var authorizationDecisionMock = mock(AuthorizationDecision.class);

                when(authorizationDecisionInterpreterMock
                        .constructAuthorizationDecision(eq(AuthorizationDecisionType.PERMIT), any(), any(), any()))
                        .thenAnswer(invocationOnMock -> {
                            final StringLiteral resource  = invocationOnMock.getArgument(1);
                            final List<StringLiteral> obligations = invocationOnMock.getArgument(2);
                            final List<StringLiteral> advice = invocationOnMock.getArgument(3);

                            assertEquals("resource", resource.getString());
                            assertEquals("obligation", obligations.get(0).getString());
                            assertEquals("advice", advice.get(0).getString());

                            return authorizationDecisionMock;
                        });

                when(expectOrVerifyStepMock.expectNext(authorizationDecisionMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Expect next with matcher")
        class ExpectNextWithMatcherTest {
            private final MockedStatic<Matchers>              saplMatchersMockedStatic     = mockStatic(Matchers.class,
                    Answers.RETURNS_SMART_NULLS);
            private final MockedStatic<org.hamcrest.Matchers> hamcrestMatchersMockedStatic = mockStatic(
                    org.hamcrest.Matchers.class, Answers.RETURNS_SMART_NULLS);

            @AfterEach
            void tearDown() {
                saplMatchersMockedStatic.close();
                hamcrestMatchersMockedStatic.close();
            }

            @Test
            void interpretRepeatedExpect_handlesNullMatchers_throwsSaplTestException() {
                final var nextWithMatcherMock         = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper
                        .mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock          = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                when(nextWithMatcherMock.getMatcher()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesEmptyMatchers_throwsSaplTestException() {
                final var nextWithMatcherMock         = mock(NextWithMatcher.class);
                final var expectOrAdjustmentStepsMock = Helper
                        .mockEList(List.<ExpectOrAdjustmentStep>of(nextWithMatcherMock));
                final var repeatedExpectMock          = mock(RepeatedExpect.class);

                when(repeatedExpectMock.getExpectSteps()).thenReturn(expectOrAdjustmentStepsMock);

                final var matchersMock = Helper.mockEList(List.<AuthorizationDecisionMatcher>of());
                when(nextWithMatcherMock.getMatcher()).thenReturn(matchersMock);

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleMatcher_returnsAdjustedVerifyStep() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect decision is permit");

                final var mappedAuthorizationDecisionMatcherMock = mock(Matcher.class);

                when(authorizationDecisionMatcherInterpreterMock
                        .getHamcrestAuthorizationDecisionMatcher(any(IsDecision.class)))
                        .thenAnswer(invocationOnMock -> {
                            final IsDecision isDecision = invocationOnMock.getArgument(0);

                            assertEquals(AuthorizationDecisionType.PERMIT, isDecision.getDecision());
                            return mappedAuthorizationDecisionMatcherMock;
                        });

                when(expectOrVerifyStepMock.expectNext(mappedAuthorizationDecisionMatcherMock))
                        .thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsMultipleMixedMatchers_returnsAdjustedVerifyStep() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect decision with obligation, is deny");

                final var hasObligationOrAdviceMappedMock = mock(Matcher.class);
                final var isDecisionMappedMock            = mock(Matcher.class);

                when(authorizationDecisionMatcherInterpreterMock
                        .getHamcrestAuthorizationDecisionMatcher(any(HasObligationOrAdvice.class)))
                        .thenAnswer(invocationOnMock -> {
                            final HasObligationOrAdvice hasObligationOrAdvice = invocationOnMock.getArgument(0);

                            assertEquals(AuthorizationDecisionMatcherType.OBLIGATION, hasObligationOrAdvice.getType());
                            assertNull(hasObligationOrAdvice.getMatcher());
                            return hasObligationOrAdviceMappedMock;
                        });

                when(authorizationDecisionMatcherInterpreterMock
                        .getHamcrestAuthorizationDecisionMatcher(any(IsDecision.class)))
                        .thenAnswer(invocationOnMock -> {
                            final IsDecision isDecision = invocationOnMock.getArgument(0);

                            assertEquals(AuthorizationDecisionType.DENY, isDecision.getDecision());
                            return isDecisionMappedMock;
                        });

                final var allOfMock = mock(Matcher.class);
                hamcrestMatchersMockedStatic
                        .when(() -> org.hamcrest.Matchers
                                .allOf(new Matcher[] { hasObligationOrAdviceMappedMock, isDecisionMappedMock }))
                        .thenReturn(allOfMock);

                when(expectOrVerifyStepMock.expectNext(allOfMock)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Attribute adjustment")
        class AttributeAdjustmentTest {
            @Test
            void interpretRepeatedExpect_interpretsAttributeAdjustment_returnsAdjustedVerifyStep() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- let attribute \"foo\" return \"bar\"");

                final var expectedVal = Val.of("bar");

                when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

                when(expectOrVerifyStepMock.thenAttribute("foo", expectedVal)).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Await")
        class AwaitTest {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- wait for \"PT5S\"");

                when(durationInterpreterMock.getJavaDurationFromDuration(any())).thenAnswer(invocationOnMock -> {
                    final io.sapl.test.grammar.sAPLTest.Duration duration = invocationOnMock.getArgument(0);

                    assertEquals("PT5S", duration.getDuration());
                    return Duration.ofSeconds(5);
                });

                when(expectOrVerifyStepMock.thenAwait(Duration.ofSeconds(5))).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("No event")
        class NoEventTest {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                final RepeatedExpect repeatedExpect = buildExpectChain("- expect no-event for \"PT3S\"");

                when(durationInterpreterMock.getJavaDurationFromDuration(any())).thenAnswer(invocationOnMock -> {
                    final io.sapl.test.grammar.sAPLTest.Duration duration = invocationOnMock.getArgument(0);

                    assertEquals("PT3S", duration.getDuration());
                    return Duration.ofSeconds(3);
                });

                when(expectOrVerifyStepMock.expectNoEvent(Duration.ofSeconds(3))).thenReturn(expectOrVerifyStepMock);

                final var result = expectInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }
    }
}
