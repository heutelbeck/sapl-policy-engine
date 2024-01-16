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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.StringWithLength;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.math.BigDecimal;
import java.util.List;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringMatcherInterpreterTest {
    private StringMatcherInterpreter stringMatcherInterpreter;
    @Mock
    Matcher<? super String>          matcherMock;

    private final MockedStatic<Matchers> matchersMockedStatic = mockStatic(Matchers.class);

    @BeforeEach
    void setUp() {
        stringMatcherInterpreter = new StringMatcherInterpreter();
    }

    @AfterEach
    void tearDown() {
        matchersMockedStatic.close();
    }

    private <T extends StringMatcher> T buildStringMatcher(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getStringMatcherRule);
    }

    @Test
    void getHamcrestStringMatcher_handlesNullStringMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> stringMatcherInterpreter.getHamcrestStringMatcher(null));

        assertEquals("Unknown type of StringMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestStringMatcher_handlesUnknownStringMatcher_throwsSaplTestException() {
        final var stringMatcherMock = mock(StringMatcher.class);

        final var exception = assertThrows(SaplTestException.class,
                () -> stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock));

        assertEquals("Unknown type of StringMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNull_returnsNullValueMatcher() {
        final var stringMatcher = buildStringMatcher("null");

        matchersMockedStatic.when(() -> Matchers.nullValue(String.class)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsBlank_returnsBlankStringMatcher() {
        final var stringMatcher = buildStringMatcher("blank");

        matchersMockedStatic.when(Matchers::blankString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEmpty_returnsEmptyStringMatcher() {
        final var stringMatcher = buildStringMatcher("empty");

        matchersMockedStatic.when(Matchers::emptyString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrEmpty_returnsEmptyOrNullStringMatcher() {
        final var stringMatcher = buildStringMatcher("null-or-empty");

        matchersMockedStatic.when(Matchers::emptyOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrBlank_returnsBlankOrNullStringMatcher() {
        final var stringMatcher = buildStringMatcher("null-or-blank");

        matchersMockedStatic.when(Matchers::blankOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualWithCompressedWhiteSpace_returnsEqualToCompressingWhiteSpaceMatcher() {
        final var stringMatcher = buildStringMatcher("equals \"foo\" with compressed whitespaces");

        matchersMockedStatic.when(() -> Matchers.equalToCompressingWhiteSpace("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualIgnoringCase_returnsEqualToIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("equals \"foo\" ignoring case");

        matchersMockedStatic.when(() -> Matchers.equalToIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringMatchesRegex_returnsMatchesRegexMatcher() {
        final var stringMatcher = buildStringMatcher("matches regex \"fooRegex\"");

        matchersMockedStatic.when(() -> Matchers.matchesRegex("fooRegex")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWith_returnsStartsWithMatcher() {
        final var stringMatcher = buildStringMatcher("starts with \"foo\"");

        matchersMockedStatic.when(() -> Matchers.startsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWithIgnoringCase_returnsStartsWithIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("starts with \"foo\" ignoring case");

        matchersMockedStatic.when(() -> Matchers.startsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWith_returnsEndsWithMatcher() {
        final var stringMatcher = buildStringMatcher("ends with \"foo\"");

        matchersMockedStatic.when(() -> Matchers.endsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWithIgnoringCase_returnsEndsWithIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("ends with \"foo\" ignoring case");

        matchersMockedStatic.when(() -> Matchers.endsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContains_returnsContainsStringMatcher() {
        final var stringMatcher = buildStringMatcher("contains \"foo\"");

        matchersMockedStatic.when(() -> Matchers.containsString("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsIgnoringCase_returnsContainsStringIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("contains \"foo\" ignoring case");

        matchersMockedStatic.when(() -> Matchers.containsStringIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsInOrder_returnsStringContainsInOrderMatcher() {
        final var stringMatcher = buildStringMatcher("contains substrings \"foo\", \"foo2\" ordered");

        matchersMockedStatic.when(() -> Matchers.stringContainsInOrder(List.of("foo", "foo2"))).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, 0.5, Integer.MAX_VALUE + 1D, -5, -0.1 })
    void getHamcrestStringMatcher_handlesStringWithLengthWithInvalidInteger_returnsHasLengthMatcher(
            double returnValue) {
        final var stringMatcherMock = mock(StringWithLength.class);
        when(stringMatcherMock.getLength()).thenReturn(BigDecimal.valueOf(returnValue));

        final var exception = assertThrows(SaplTestException.class,
                () -> stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock));

        assertEquals("String length needs to be an natural number", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, Integer.MAX_VALUE, 0, 555 })
    void getHamcrestStringMatcher_handlesStringWithLengthWithValidInteger_returnsHasLengthMatcher(int returnValue) {
        final var stringMatcherMock = mock(StringWithLength.class);
        when(stringMatcherMock.getLength()).thenReturn(BigDecimal.valueOf(returnValue));

        matchersMockedStatic.when(() -> Matchers.hasLength(returnValue)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }
}
