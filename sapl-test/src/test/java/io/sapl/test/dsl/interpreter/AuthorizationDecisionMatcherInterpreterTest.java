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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.hamcrest.HasAdvice;
import io.sapl.hamcrest.HasAdviceContainingKeyValue;
import io.sapl.hamcrest.HasObligation;
import io.sapl.hamcrest.HasObligationContainingKeyValue;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.AnyDecision;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sapltest.DefaultObjectMatcher;
import io.sapl.test.grammar.sapltest.ExtendedObjectMatcher;
import io.sapl.test.grammar.sapltest.HasObligationOrAdvice;
import io.sapl.test.grammar.sapltest.HasResource;
import io.sapl.test.grammar.sapltest.IsDecision;
import io.sapl.test.grammar.sapltest.IsJsonNull;
import io.sapl.test.grammar.sapltest.NullLiteral;
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
class AuthorizationDecisionMatcherInterpreterTests {
    @Mock
    private ValueInterpreter                        valueInterpreterMock;
    @Mock
    private JsonNodeMatcherInterpreter              jsonNodeMatcherInterpreterMock;
    @InjectMocks
    private AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;

    @Mock
    Matcher<JsonNode> jsonNodeMatcherMock;

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

    private <T extends AuthorizationDecisionMatcher> T buildAuthorizationDecisionMatcher(final String input,
            Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getAuthorizationDecisionMatcherRule, clazz);
    }

    private IsDecision buildIsDecisionMatcher(final String input) {
        return buildAuthorizationDecisionMatcher(input, IsDecision.class);
    }

    private HasObligationOrAdvice buildHasObligationOrAdviceMatcher(final String input) {
        return buildAuthorizationDecisionMatcher(input, HasObligationOrAdvice.class);
    }

    private HasResource buildHasResourceMatcher(final String input) {
        return buildAuthorizationDecisionMatcher(input, HasResource.class);
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
        final var anyDecision = buildAuthorizationDecisionMatcher("any", AnyDecision.class);

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
        final var isDecision = buildIsDecisionMatcher("is permit");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isPermit).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionDeny_returnsDenyMatcher() {
        final var isDecision = buildIsDecisionMatcher("is deny");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isDeny).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionIndeterminate_returnsIndeterminateMatcher() {
        final var isDecision = buildIsDecisionMatcher("is indeterminate");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isIndeterminate).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionNotApplicable_returnsNotApplicableMatcher() {
        final var isDecision = buildIsDecisionMatcher("is notApplicable");

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isNotApplicable).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecision);

        assertEquals(matcherMock, result);
    }

    @Nested
    @DisplayName("HasObligationOrAdvice tests")
    class HasObligationOrAdviceTests {

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sapltest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sapltest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceNullMatcherForObligation_returnsHasAnyObligation() {
            final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with obligation");

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation)
                    .thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithNullMatcherForAdvice_returnsHasAnyAdvice() {
            final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with advice");

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sapltest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sapltest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Nested
        @DisplayName("ObjectWithExactMatch cases")
        class ObjectWithExactMatchTests {
            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchWithNullValForObligation_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with obligation equals null");

                when(valueInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Val to match is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchWithNullValForAdvice_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with advice equals null");

                when(valueInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Val to match is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForObligation_returnsHasObligationWithMatcher() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with obligation equals \"5\"");

                final var expectedVal = Val.of("5");
                when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

                hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(jsonNodeMatcherMock);

                final var hasObligationMatcherMock = mock(HasObligation.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(jsonNodeMatcherMock))
                        .thenReturn(hasObligationMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasObligationMatcherMock, result);
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForAdvice_returnsHasAdviceWithMatcher() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with advice equals \"5\"");

                final var expectedVal = Val.of("5");
                when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

                hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(jsonNodeMatcherMock);

                final var hasAdviceMatcherMock = mock(HasAdvice.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(jsonNodeMatcherMock))
                        .thenReturn(hasAdviceMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasAdviceMatcherMock, result);
            }
        }

        @Nested
        @DisplayName("ObjectWithMatcher cases")
        class ObjectWithMatcherTests {
            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligationWithNullMappedMatcher_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with obligation matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Matcher for JsonNode is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdviceWithNullMappedMatcher_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with advice matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Matcher for JsonNode is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligation_returnsHasObligationWithMatcher() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with obligation matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                        .thenReturn(jsonNodeMatcherMock);

                final var hasObligationMatcherMock = mock(HasObligation.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(jsonNodeMatcherMock))
                        .thenReturn(hasObligationMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasObligationMatcherMock, result);
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdvice_returnsHasAdviceWithMatcher() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher("with advice matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                        .thenReturn(jsonNodeMatcherMock);

                final var hasAdviceMatcherMock = mock(HasAdvice.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(jsonNodeMatcherMock))
                        .thenReturn(hasAdviceMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasAdviceMatcherMock, result);
            }
        }

        @Nested
        @DisplayName("ObjectWithKeyValueMatcher cases")
        class ObjectWithKeyValueMatcherTests {
            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligationWithoutValueMatcher_returnsHasObligationWithKey() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with obligation containing key \"foo\"");

                final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo"))
                        .thenReturn(hasObligationMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasObligationMatcherMock, result);
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdviceWithoutValueMatcher_returnsHasAdviceWithKey() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with advice containing key \"foo\"");

                final var hasAdviceContainingKeyValue = mock(HasAdviceContainingKeyValue.class);
                saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue("foo"))
                        .thenReturn(hasAdviceContainingKeyValue);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasAdviceContainingKeyValue, result);
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherWithNullMappedValueMatcherForObligation_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with obligation containing key \"foo\" with value matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Matcher for JsonNode is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherWithNullMappedValueMatcherForAdvice_throwsSaplTestException() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with advice containing key \"foo\" with value matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> authorizationDecisionMatcherInterpreter
                                .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice));

                assertEquals("Matcher for JsonNode is null", exception.getMessage());
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligation_returnsHasObligationWithKeyValue() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with obligation containing key \"foo\" with value matching null");

                when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                        .thenReturn(jsonNodeMatcherMock);

                final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
                saplMatchersMockedStatic.when(
                        () -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo", jsonNodeMatcherMock))
                        .thenReturn(hasObligationMatcherMock);

                final var result = authorizationDecisionMatcherInterpreter
                        .getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdvice);

                assertEquals(hasObligationMatcherMock, result);
            }

            @Test
            void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdvice_returnsHasAdviceWithKeyValue() {
                final var hasObligationOrAdvice = buildHasObligationOrAdviceMatcher(
                        "with advice containing key \"foo\" with value matching null");

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
        }
    }

    @Nested
    @DisplayName("HasResource tests")
    class HasResourceTests {
        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatchWithNullMatcher_throwsSaplTestException() {
            final var hasResource = buildHasResourceMatcher("with resource equals null");

            when(valueInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResource));

            assertEquals("Val to match is null", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcherWithNullMatcher_throwsSaplTestException() {
            final var hasResource = buildHasResourceMatcher("with resource matching null");

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResource));

            assertEquals("Matcher for JsonNode is null", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatch_returnsHasResourceWithMatcher() {
            final var hasResource = buildHasResourceMatcher("with resource equals null");

            final var expectedVal = Val.NULL;
            when(valueInterpreterMock.getValFromValue(any(NullLiteral.class))).thenReturn(expectedVal);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(expectedVal.get())).thenReturn(jsonNodeMatcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(jsonNodeMatcherMock))
                    .thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcher_returnsHasResourceWithMatcher() {
            final var hasResource = buildHasResourceMatcher("with resource matching null");

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                    .thenReturn(jsonNodeMatcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(jsonNodeMatcherMock))
                    .thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter
                    .getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithDefaultObjectMatcherWithUnknownType_throwsSaplTestException() {
            final var hasResource = mock(io.sapl.test.grammar.sapltest.HasResource.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasResource.getMatcher()).thenReturn(defaultObjectMatcher);

            final var exception = assertThrows(SaplTestException.class,
                    () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResource));

            assertEquals("Unknown type of DefaultObjectMatcher", exception.getMessage());
        }
    }

}
