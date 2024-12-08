/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
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

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.hamcrest.Matchers;
import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.AdjustBlock;
import io.sapl.test.grammar.sapltest.AdjustStep;
import io.sapl.test.grammar.sapltest.AnyDecision;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionType;
import io.sapl.test.grammar.sapltest.ExpectBlock;
import io.sapl.test.grammar.sapltest.ExpectOrAdjustBlock;
import io.sapl.test.grammar.sapltest.ExpectStep;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.HasObligationOrAdvice;
import io.sapl.test.grammar.sapltest.IsDecision;
import io.sapl.test.grammar.sapltest.Multiple;
import io.sapl.test.grammar.sapltest.Next;
import io.sapl.test.grammar.sapltest.NextWithDecision;
import io.sapl.test.grammar.sapltest.NextWithMatcher;
import io.sapl.test.grammar.sapltest.NumericAmount;
import io.sapl.test.grammar.sapltest.RepeatedExpect;
import io.sapl.test.grammar.sapltest.SingleExpect;
import io.sapl.test.grammar.sapltest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;

@ExtendWith(MockitoExtension.class)
class ExpectationInterpreterTests {
    @Mock
    protected ValueInterpreter                        valueInterpreterMock;
    @Mock
    protected AuthorizationDecisionInterpreter        authorizationDecisionInterpreterMock;
    @Mock
    protected AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreterMock;
    @Mock
    protected DurationInterpreter                     durationInterpreterMock;
    @Mock
    protected MultipleInterpreter                     multipleInterpreterMock;
    @InjectMocks
    protected ExpectationInterpreter                  expectationInterpreter;
    @Mock
    protected ExpectOrVerifyStep                      expectOrVerifyStepMock;

    @Mock
    protected Matcher<AuthorizationDecision> authorizationDecisionMatcherMock;

    private final MockedStatic<org.hamcrest.Matchers> hamcrestMatchersMockedStatic = mockStatic(
            org.hamcrest.Matchers.class, Answers.RETURNS_SMART_NULLS);

    @AfterEach
    void tearDown() {
        hamcrestMatchersMockedStatic.close();
    }

    private <T extends Expectation> T buildExpectation(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getExpectationRule, clazz);
    }

    private SingleExpect buildSingleExpect(final String input) {
        return buildExpectation(input, SingleExpect.class);
    }

    private SingleExpectWithMatcher buildSingleExpectWithMatcher(final String input) {
        return buildExpectation(input, SingleExpectWithMatcher.class);
    }

    private RepeatedExpect buildRepeatedExpect(final String input) {
        return buildExpectation(input, RepeatedExpect.class);
    }

    @Nested
    @DisplayName("Single expect")
    class SingleExpectTests {
        @Test
        void interpretSingleExpect_handlesNullExpectStep_throwsSaplTestException() {
            final var singleExpect = buildSingleExpect("expect permit");

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpect(null, singleExpect));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullSingleExpect_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpect(expectOrVerifyStepMock, null));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullExpectStepAndNullSingleExpect_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpect(null, null));

            assertEquals("ExpectOrVerifyStep or singleExpect is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_handlesNullDecisionInSingleExpect_throwsSaplTestException() {
            final var singleExpectMock = mock(SingleExpect.class);

            when(singleExpectMock.getDecision()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpect(expectOrVerifyStepMock, singleExpectMock));

            assertEquals("AuthorizationDecision is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpect_callsAuthorizationDecisionInterpreter_returnsVerifyStep() {
            final var singleExpect = buildSingleExpect(
                    "expect permit with obligations \"obligation\" with resource \"resource\" with advice \"advice1\", \"advice2\"");

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

            final var result = expectationInterpreter.interpretSingleExpect(expectOrVerifyStepMock, singleExpect);

            assertEquals(verifyStepMock, result);
        }
    }

    @Nested
    @DisplayName("Single expect with matcher")
    class SingleExpectWithMatcherTests {

        @Test
        void interpretSingleExpectWithMatcher_handlesNullExpectStep_throwsSaplTestException() {
            final var singleExpectWithMatcher = buildSingleExpectWithMatcher("expect decision matching any");

            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpectWithMatcher(null, singleExpectWithMatcher));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullSingleExpectWithMatcher_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpectWithMatcher(expectOrVerifyStepMock, null));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullExpectStepAndNullSingleExpectWithMatcher_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> expectationInterpreter.interpretSingleExpectWithMatcher(null, null));

            assertEquals("ExpectOrVerifyStep or singleExpectWithMatcher is null", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesNullMatchersInSingleExpectWithMatcher_throwsSaplTestException() {
            final var singleExpectWithMatcherMock = mock(SingleExpectWithMatcher.class);

            when(singleExpectWithMatcherMock.getMatchers()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                    .interpretSingleExpectWithMatcher(expectOrVerifyStepMock, singleExpectWithMatcherMock));

            assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_handlesEmptyMatchersInSingleExpectWithMatcher_throwsSaplTestException() {
            final var singleExpectWithMatcherMock = mock(SingleExpectWithMatcher.class);

            TestHelper.mockEListResult(singleExpectWithMatcherMock::getMatchers, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                    .interpretSingleExpectWithMatcher(expectOrVerifyStepMock, singleExpectWithMatcherMock));

            assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
        }

        @Test
        void interpretSingleExpectWithMatcher_callsAuthorizationDecisionMatcherInterpreter_returnsVerifyStep() {
            final var singleExpectWithMatcher = buildSingleExpectWithMatcher("expect decision any");

            when(authorizationDecisionMatcherInterpreterMock
                    .getHamcrestAuthorizationDecisionMatcher(any(AnyDecision.class)))
                    .thenReturn(authorizationDecisionMatcherMock);

            final var verifyStepMock = mock(VerifyStep.class);
            when(expectOrVerifyStepMock.expect(authorizationDecisionMatcherMock)).thenReturn(verifyStepMock);

            final var result = expectationInterpreter.interpretSingleExpectWithMatcher(expectOrVerifyStepMock,
                    singleExpectWithMatcher);

            assertEquals(verifyStepMock, result);
        }

        @Test
        void interpretSingleExpectWithMatcher_correctlyMapsMultipleMixedMatchers_returnsVerifyStep() {
            final var singleExpectWithMatcher = buildSingleExpectWithMatcher(
                    "expect decision with obligation, is deny");

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
                    .getHamcrestAuthorizationDecisionMatcher(any(IsDecision.class))).thenAnswer(invocationOnMock -> {
                        final IsDecision isDecision = invocationOnMock.getArgument(0);

                        assertEquals(AuthorizationDecisionType.DENY, isDecision.getDecision());
                        return isDecisionMappedMock;
                    });

            hamcrestMatchersMockedStatic
                    .when(() -> org.hamcrest.Matchers
                            .allOf(List.of(hasObligationOrAdviceMappedMock, isDecisionMappedMock)
                                    .<Matcher<AuthorizationDecision>>toArray(Matcher[]::new)))
                    .thenReturn(authorizationDecisionMatcherMock);

            when(expectOrVerifyStepMock.expect(authorizationDecisionMatcherMock)).thenReturn(expectOrVerifyStepMock);

            final var result = expectationInterpreter.interpretSingleExpectWithMatcher(expectOrVerifyStepMock,
                    singleExpectWithMatcher);

            assertEquals(expectOrVerifyStepMock, result);
        }
    }

    @Nested
    @DisplayName("Repeated expect")
    class RepeatedExpectTests {

        @Nested
        @DisplayName("Error cases")
        class ErrorCasesTests {
            @Test
            void interpretRepeatedExpect_handlesNullExpectStep_throwsSaplTestException() {
                final var repeatedExpect = buildRepeatedExpect("expect - permit once");

                final var exception = assertThrows(SaplTestException.class,
                        () -> expectationInterpreter.interpretRepeatedExpect(null, repeatedExpect));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullRepeatedExpect_throwsSaplTestException() {
                final var exception = assertThrows(SaplTestException.class,
                        () -> expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock, null));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullExpectStepAndNullRepeatedExpect_throwsSaplTestException() {
                final var exception = assertThrows(SaplTestException.class,
                        () -> expectationInterpreter.interpretRepeatedExpect(null, null));

                assertEquals("ExpectOrVerifyStep or repeatedExpect is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullExpectOrAdjustBlocks_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                when(repeatedExpectMock.getExpectOrAdjustBlocks()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No ExpectOrAdjustStep found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectOrAdjustBlocksBeingEmpty_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, Collections.emptyList());

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No ExpectOrAdjustStep found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNullInExpectOrAdjustBlocks_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        Arrays.asList(expectBlockMock, null));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("ExpectOrAdjustBlock is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesAdjacentExpectBlocks_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlock1Mock   = mock(ExpectBlock.class);
                final var expectBlock2Mock   = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(expectBlock1Mock, expectBlock2Mock));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Expect and Adjust Blocks need to alternate", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesAdjacentAdjustBlocks_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var adjustBlock1Mock   = mock(AdjustBlock.class);
                final var adjustBlock2Mock   = mock(AdjustBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(adjustBlock1Mock, adjustBlock2Mock));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Expect and Adjust Blocks need to alternate", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesEndingWithAdjustBlock_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);
                final var adjustBlockMock    = mock(AdjustBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(expectBlockMock, adjustBlockMock));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Last block needs to be an ExpectBlock", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectBlockWithNullSteps_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));

                when(expectBlockMock.getExpectSteps()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("ExpectBlock has no Steps", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectBlockWithEmptySteps_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));

                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, Collections.emptyList());

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("ExpectBlock has no Steps", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesExpectBlockWithUnknownStep_throwsSaplTestException() {
                final var repeatedExpectMock    = mock(RepeatedExpect.class);
                final var expectBlockMock       = mock(ExpectBlock.class);
                final var unknownExpectStepMock = mock(ExpectStep.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));

                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, List.of(unknownExpectStepMock));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of ExpectStep", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesAdjustBlockWithNullSteps_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var adjustBlockMock    = mock(AdjustBlock.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(adjustBlockMock, expectBlockMock));

                when(adjustBlockMock.getAdjustSteps()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("AdjustBlock has no Steps", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesAdjustBlockWithEmptySteps_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var adjustBlockMock    = mock(AdjustBlock.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(adjustBlockMock, expectBlockMock));
                TestHelper.mockEListResult(adjustBlockMock::getAdjustSteps, Collections.emptyList());

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("AdjustBlock has no Steps", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesAdjustBlockWithUnknownStep_throwsSaplTestException() {
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var adjustBlockMock    = mock(AdjustBlock.class);
                final var expectBlockMock    = mock(ExpectBlock.class);
                final var unknownAdjustStep  = mock(AdjustStep.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(adjustBlockMock, expectBlockMock));
                TestHelper.mockEListResult(adjustBlockMock::getAdjustSteps, List.of(unknownAdjustStep));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of AdjustStep", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesUnknownExpectOrAdjustBlock_throwsSaplTestException() {
                final var unknownExpectOrAdjustBlockMock = mock(ExpectOrAdjustBlock.class);
                final var repeatedExpectMock             = mock(RepeatedExpect.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks,
                        List.of(unknownExpectOrAdjustBlockMock));

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of ExpectOrAdjustBlock", exception.getMessage());
            }
        }

        @Nested
        @DisplayName("Expect next")
        class ExpectNextTests {

            @Test
            void interpretRepeatedExpect_constructsNextPermitWithUnknownAmount_throwsSaplTestException() {
                final var nextMock           = mock(Next.class);
                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, List.of(nextMock));
                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));

                final var numericAmountMock = mock(NumericAmount.class);
                when(nextMock.getAmount()).thenReturn(numericAmountMock);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("Unknown type of NumericAmount", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_constructsNextDenyWithNumericAmountBeingMultiple_returnsVerifyStepWithNextDeny() {
                final var repeatedExpect = buildRepeatedExpect("expect - deny 3 times");

                when(multipleInterpreterMock.getAmountFromMultiple(any())).thenAnswer(invocationOnMock -> {
                    final Multiple multiple = invocationOnMock.getArgument(0);

                    assertEquals(3, multiple.getAmount().intValueExact());
                    return 3;
                });

                when(expectOrVerifyStepMock.expectNextDeny(3)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextIndeterminateWithNumericAmountBeingOnce_returnsVerifyStepWithNextIndeterminateOnce() {
                final var repeatedExpect = buildRepeatedExpect("expect - indeterminate once");

                when(expectOrVerifyStepMock.expectNextIndeterminate(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingOnce_returnsVerifyStepWithNextPermitOnce() {
                final var repeatedExpect = buildRepeatedExpect("expect - permit once");

                when(expectOrVerifyStepMock.expectNextPermit(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_constructsNextNotApplicableWithNumericAmountBeingMultiple_returnsVerifyStepWithNextNotApplicable() {
                final var repeatedExpect = buildRepeatedExpect("expect - notApplicable 5 times");

                when(multipleInterpreterMock.getAmountFromMultiple(any())).thenAnswer(invocationOnMock -> {
                    final Multiple multiple = invocationOnMock.getArgument(0);

                    assertEquals(5, multiple.getAmount().intValueExact());
                    return 5;
                });

                when(expectOrVerifyStepMock.expectNextNotApplicable(5)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Expect next with decision")
        class ExpectNextWithDecisionTests {
            @Test
            void interpretRepeatedExpect_handlesNextWithNullDecision_throwsSaplTestException() {
                final var nextWithDecisionMock = mock(NextWithDecision.class);

                final var repeatedExpectMock = mock(RepeatedExpect.class);
                final var expectBlockMock    = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));
                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, List.of(nextWithDecisionMock));

                when(nextWithDecisionMock.getExpectedDecision()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("AuthorizationDecision is null", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesNextWithAuthorizationDecision_returnsVerifyStepWithNextAuthorizationDecision() {
                final var repeatedExpect = buildRepeatedExpect(
                        "expect - permit with obligations \"obligation\" with resource \"resource\" with advice \"advice\"");

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

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Expect next with matcher")
        class ExpectNextWithMatcherTests {
            private final MockedStatic<Matchers> saplMatchersMockedStatic = mockStatic(Matchers.class,
                    Answers.RETURNS_SMART_NULLS);

            @AfterEach
            void tearDown() {
                saplMatchersMockedStatic.close();
            }

            @Test
            void interpretRepeatedExpect_handlesNullMatchers_throwsSaplTestException() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var repeatedExpectMock  = mock(RepeatedExpect.class);
                final var expectBlockMock     = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));
                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, List.of(nextWithMatcherMock));

                when(nextWithMatcherMock.getMatcher()).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_handlesEmptyMatchers_throwsSaplTestException() {
                final var nextWithMatcherMock = mock(NextWithMatcher.class);
                final var repeatedExpectMock  = mock(RepeatedExpect.class);
                final var expectBlockMock     = mock(ExpectBlock.class);

                TestHelper.mockEListResult(repeatedExpectMock::getExpectOrAdjustBlocks, List.of(expectBlockMock));
                TestHelper.mockEListResult(expectBlockMock::getExpectSteps, List.of(nextWithMatcherMock));
                TestHelper.mockEListResult(nextWithMatcherMock::getMatcher, Collections.emptyList());

                final var exception = assertThrows(SaplTestException.class, () -> expectationInterpreter
                        .interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock));

                assertEquals("No AuthorizationDecisionMatcher found", exception.getMessage());
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsSingleMatcher_returnsAdjustedVerifyStep() {
                final var repeatedExpect = buildRepeatedExpect("expect - decision is permit");

                when(authorizationDecisionMatcherInterpreterMock
                        .getHamcrestAuthorizationDecisionMatcher(any(IsDecision.class)))
                        .thenAnswer(invocationOnMock -> {
                            final IsDecision isDecision = invocationOnMock.getArgument(0);

                            assertEquals(AuthorizationDecisionType.PERMIT, isDecision.getDecision());
                            return authorizationDecisionMatcherMock;
                        });

                when(expectOrVerifyStepMock.expectNext(authorizationDecisionMatcherMock))
                        .thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }

            @Test
            void interpretRepeatedExpect_correctlyMapsMultipleMixedMatchers_returnsAdjustedVerifyStep() {
                final var repeatedExpect = buildRepeatedExpect("expect - decision with obligation, is deny");

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

                hamcrestMatchersMockedStatic
                        .when(() -> org.hamcrest.Matchers
                                .allOf(List.of(hasObligationOrAdviceMappedMock, isDecisionMappedMock)
                                        .<Matcher<AuthorizationDecision>>toArray(Matcher[]::new)))
                        .thenReturn(authorizationDecisionMatcherMock);

                when(expectOrVerifyStepMock.expectNext(authorizationDecisionMatcherMock))
                        .thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("No event")
        class NoEventTests {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                final var repeatedExpect = buildRepeatedExpect("expect - no-event for \"PT3S\"");

                when(durationInterpreterMock.getJavaDurationFromDuration(any())).thenAnswer(invocationOnMock -> {
                    final io.sapl.test.grammar.sapltest.Duration duration = invocationOnMock.getArgument(0);

                    assertEquals("PT3S", duration.getDuration());
                    return Duration.ofSeconds(3);
                });

                when(expectOrVerifyStepMock.expectNoEvent(Duration.ofSeconds(3))).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Attribute adjustment")
        class AttributeAdjustmentTests {
            @Test
            void interpretRepeatedExpect_interpretsAttributeAdjustment_returnsAdjustedVerifyStep() {
                // expect required since chain has to end with expect block
                final var repeatedExpect = buildRepeatedExpect(
                        "then - attribute \"foo\" emits \"bar\" expect - permit once");

                final var expectedVal = Val.of("bar");

                when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar")))
                        .thenReturn(expectedVal);

                when(expectOrVerifyStepMock.thenAttribute("foo", expectedVal)).thenReturn(expectOrVerifyStepMock);
                // required since chain has to end with expect block
                when(expectOrVerifyStepMock.expectNextPermit(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }

        @Nested
        @DisplayName("Await")
        class AwaitTests {
            @Test
            void interpretRepeatedExpect_interpretsAwait_returnsAdjustedVerifyStep() {
                // expect required since chain has to end with expect block
                final var repeatedExpect = buildRepeatedExpect("then - wait for \"PT5S\" expect - permit once");

                when(durationInterpreterMock.getJavaDurationFromDuration(any())).thenAnswer(invocationOnMock -> {
                    final io.sapl.test.grammar.sapltest.Duration duration = invocationOnMock.getArgument(0);

                    assertEquals("PT5S", duration.getDuration());
                    return Duration.ofSeconds(5);
                });

                when(expectOrVerifyStepMock.thenAwait(Duration.ofSeconds(5))).thenReturn(expectOrVerifyStepMock);
                // required since chain has to end with expect block
                when(expectOrVerifyStepMock.expectNextPermit(1)).thenReturn(expectOrVerifyStepMock);

                final var result = expectationInterpreter.interpretRepeatedExpect(expectOrVerifyStepMock,
                        repeatedExpect);

                assertEquals(expectOrVerifyStepMock, result);
            }
        }
    }
}
