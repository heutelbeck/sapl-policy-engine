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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.api.interpreter.Val;
import io.sapl.hamcrest.HasAdvice;
import io.sapl.hamcrest.HasAdviceContainingKeyValue;
import io.sapl.hamcrest.HasObligation;
import io.sapl.hamcrest.HasObligationContainingKeyValue;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sAPLTest.DefaultObjectMatcher;
import io.sapl.test.grammar.sAPLTest.ExtendedObjectMatcher;
import io.sapl.test.grammar.sAPLTest.IsDecision;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.NullLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionMatcherInterpreterTest {
    @Mock
    private ValInterpreter                          valInterpreterMock;
    @Mock
    private JsonNodeMatcherInterpreter              jsonNodeMatcherInterpreterMock;
    @InjectMocks
    private AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;

    private final MockedStatic<Matchers>                  hamcrestMatchersMockedStatic     = mockStatic(Matchers.class);
    private final MockedStatic<CoreMatchers>              hamcrestCoreMatchersMockedStatic = mockStatic(
            CoreMatchers.class);
    private final MockedStatic<io.sapl.hamcrest.Matchers> saplMatchersMockedStatic         = mockStatic(
            io.sapl.hamcrest.Matchers.class);

    @AfterEach
    void tearDown() {
        hamcrestMatchersMockedStatic.close();
        hamcrestCoreMatchersMockedStatic.close();
        saplMatchersMockedStatic.close();
    }

    private <T extends AuthorizationDecisionMatcher> T buildAuthorizationDecisionMatcher(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getAuthorizationDecisionMatcherRule);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesNullAuthorizationDecisionMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(null));

        assertEquals("Unknown type of AuthorizationDecisionMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesUnknownAuthorizationDecisionMatcher_throwsSaplTestException() {
        final var authorizationDecisionMatcherMock = mock(AuthorizationDecisionMatcher.class);

        final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                .getHamcrestAuthorizationDecisionMatcher(authorizationDecisionMatcherMock));

        assertEquals("Unknown type of AuthorizationDecisionMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesAnyDecision_returnsAnyDecisionMatcher() {
        final var anyDecision = buildAuthorizationDecisionMatcher("any");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::anyDecision).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(anyDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesNullDecision_throwsSaplTestException() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock));

        assertEquals("Decision is null", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionPermit_returnsPermitMatcher() {
        final var isDecision = buildAuthorizationDecisionMatcher("is permit");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isPermit).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionDeny_returnsDenyMatcher() {
        final var isDecision = buildAuthorizationDecisionMatcher("is deny");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isDeny).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionIndeterminate_returnsIndeterminateMatcher() {
        final var isDecision = buildAuthorizationDecisionMatcher("is indeterminate");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isIndeterminate).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionNotApplicable_returnsNotApplicableMatcher() {
        final var isDecision = buildAuthorizationDecisionMatcher("is notApplicable");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isNotApplicable).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Nested
    @DisplayName("HasObligationOrAdvice tests")
    class HasObligationOrAdvice {
        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasObligationMatcherMock);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasObligationMatcherMock);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForObligationWithNullMatcher_returnsHasAnyObligation() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation equals \"5\"");

            final var expectedVal = Val.of(5);
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(null);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForAdviceWithNullMatcher_returnsHasAnyAdvice() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice equals \"5\"");

            final var expectedVal = Val.of("5");
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(null);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForObligation_returnsHasObligationWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation equals \"5\"");

            final var expectedVal = Val.of("5");
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(isMatcherMock);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(isMatcherMock))
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForAdvice_returnsHasAdviceWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice equals \"5\"");

            final var expectedVal = Val.of("5");
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(isMatcherMock);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(isMatcherMock))
                    .thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligationWithNullMatcher_returnsHasAnyObligation() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation matching null");

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdviceWithNullMatcher_returnsHasAnyAdvice() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice matching null");

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligation_returnsHasObligationWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation matching null");

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(matcherMock);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(matcherMock))
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdvice_returnsHasAdviceWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice matching null");

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(matcherMock);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(matcherMock))
                    .thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligationWithNullValue_returnsHasObligationWithKey() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation containing key \"foo\"");

            final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo"))
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdviceWithNullValue_returnsHasAdviceWithKey() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice containing key \"foo\"");

            final var hasAdviceContainingKeyValue = mock(HasAdviceContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue("foo"))
                    .thenReturn(hasAdviceContainingKeyValue);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceContainingKeyValue, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligation_returnsHasObligationWithKeyValue() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation containing key \"foo\" value null");

            final var jsonNodeMatcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(jsonNodeMatcherMock);

            final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
            saplMatchersMockedStatic
                    .when(() -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo", jsonNodeMatcherMock))
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdvice_returnsHasAdviceWithKeyValue() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice containing key \"foo\" value null");

            final var jsonNodeMatcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(jsonNodeMatcherMock);

            final var hasAdviceContainingKeyValueMock = mock(HasAdviceContainingKeyValue.class);
            saplMatchersMockedStatic
                    .when(() -> io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue("foo", jsonNodeMatcherMock))
                    .thenReturn(hasAdviceContainingKeyValueMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceContainingKeyValueMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithNullObjectMatcherForObligation_returnsHasObligation() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with obligation");

            final var hasAdviceContainingKeyValueMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasAdviceContainingKeyValueMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceContainingKeyValueMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithNullObjectMatcherForAdvice_returnsHasAdvice() {
            final io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice hasObligationOrAdvice = buildAuthorizationDecisionMatcher(
                    "with advice");

            final var hasAdviceContainingKeyValueMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice)
                    .thenReturn(hasAdviceContainingKeyValueMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceContainingKeyValueMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("HasResource tests")
    class HasResourceTests {
        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatchWithNullMatcher_returnsHasAnyResource() {
            final io.sapl.test.grammar.sAPLTest.HasResource hasResource = buildAuthorizationDecisionMatcher(
                    "with resource equals null");

            final var expectedVal = Val.NULL;
            when(valInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(expectedVal);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(null);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasResource).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatch_returnsHasResourceWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasResource hasResource = buildAuthorizationDecisionMatcher(
                    "with resource equals null");

            final var expectedVal = Val.NULL;
            when(valInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(expectedVal);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(isMatcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(isMatcherMock))
                    .thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcherWithNullMatcher_returnsHasAnyResource() {
            final io.sapl.test.grammar.sAPLTest.HasResource hasResource = buildAuthorizationDecisionMatcher(
                    "with resource matching null");

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasResource).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcher_returnsHasResourceWithMatcher() {
            final io.sapl.test.grammar.sAPLTest.HasResource hasResource = buildAuthorizationDecisionMatcher(
                    "with resource matching null");

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(matcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(matcherMock))
                    .thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithDefaultObjectMatcherWithUnknownType_throwsSaplTestException() {
            final var hasResource = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasResource.getMatcher()).thenReturn(defaultObjectMatcher);

            final var exception = assertThrows(SaplTestException.class,
                    () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResource));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }
    }

}
