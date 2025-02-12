/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.StringContains;
import io.sapl.test.grammar.sapltest.StringContainsInOrder;
import io.sapl.test.grammar.sapltest.StringEndsWith;
import io.sapl.test.grammar.sapltest.StringIsBlank;
import io.sapl.test.grammar.sapltest.StringIsEmpty;
import io.sapl.test.grammar.sapltest.StringIsEqualIgnoringCase;
import io.sapl.test.grammar.sapltest.StringIsEqualWithCompressedWhiteSpace;
import io.sapl.test.grammar.sapltest.StringIsNull;
import io.sapl.test.grammar.sapltest.StringIsNullOrBlank;
import io.sapl.test.grammar.sapltest.StringIsNullOrEmpty;
import io.sapl.test.grammar.sapltest.StringMatcher;
import io.sapl.test.grammar.sapltest.StringMatchesRegex;
import io.sapl.test.grammar.sapltest.StringStartsWith;
import io.sapl.test.grammar.sapltest.StringWithLength;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;

@ExtendWith(MockitoExtension.class)
class StringMatcherInterpreterTests {
    protected StringMatcherInterpreter stringMatcherInterpreter;
    @Mock
    protected Matcher<? super String>  matcherMock;

    protected final MockedStatic<Matchers> matchersMockedStatic = mockStatic(Matchers.class);

    protected final MockedStatic<Pattern> patternMockedStatic = mockStatic(Pattern.class, Answers.CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() {
        stringMatcherInterpreter = new StringMatcherInterpreter();
    }

    @AfterEach
    void tearDown() {
        matchersMockedStatic.close();
        patternMockedStatic.close();
    }

    private <T extends StringMatcher> T buildStringMatcher(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getStringMatcherRule, clazz);
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
        final var stringMatcher = buildStringMatcher("null", StringIsNull.class);

        matchersMockedStatic.when(() -> Matchers.nullValue(String.class)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsBlank_returnsBlankStringMatcher() {
        final var stringMatcher = buildStringMatcher("blank", StringIsBlank.class);

        matchersMockedStatic.when(Matchers::blankString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEmpty_returnsEmptyStringMatcher() {
        final var stringMatcher = buildStringMatcher("empty", StringIsEmpty.class);

        matchersMockedStatic.when(Matchers::emptyString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrEmpty_returnsEmptyOrNullStringMatcher() {
        final var stringMatcher = buildStringMatcher("null-or-empty", StringIsNullOrEmpty.class);

        matchersMockedStatic.when(Matchers::emptyOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrBlank_returnsBlankOrNullStringMatcher() {
        final var stringMatcher = buildStringMatcher("null-or-blank", StringIsNullOrBlank.class);

        matchersMockedStatic.when(Matchers::blankOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualWithCompressedWhiteSpace_returnsEqualToCompressingWhiteSpaceMatcher() {
        final var stringMatcher = buildStringMatcher("equal to \"foo\" with compressed whitespaces",
                StringIsEqualWithCompressedWhiteSpace.class);

        matchersMockedStatic.when(() -> Matchers.equalToCompressingWhiteSpace("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualIgnoringCase_returnsEqualToIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("equal to \"foo\" case-insensitive",
                StringIsEqualIgnoringCase.class);

        matchersMockedStatic.when(() -> Matchers.equalToIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringMatchesRegex_returnsMatchesRegexMatcher() {
        final var stringMatcher = buildStringMatcher("with regex \"fooRegex\"", StringMatchesRegex.class);

        final var patternMock = mock(Pattern.class);
        patternMockedStatic.when(() -> Pattern.compile("fooRegex")).thenReturn(patternMock);

        matchersMockedStatic.when(() -> Matchers.matchesRegex(patternMock)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringMatchesRegexWithInvalidRegex_throwsSaplTestException() {
        final var stringMatcher = buildStringMatcher("with regex \"invalid\"", StringMatchesRegex.class);

        final var expectedException = new PatternSyntaxException("invalid format", "invalid", -1);
        patternMockedStatic.when(() -> Pattern.compile("invalid")).thenThrow(expectedException);

        final var exception = assertThrows(SaplTestException.class,
                () -> stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher));

        assertEquals("The given regex has an invalid format", exception.getMessage());
        assertEquals(expectedException, exception.getCause());
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWith_returnsStartsWithMatcher() {
        final var stringMatcher = buildStringMatcher("starting with \"foo\"", StringStartsWith.class);

        matchersMockedStatic.when(() -> Matchers.startsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWithIgnoringCase_returnsStartsWithIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("starting with \"foo\" case-insensitive", StringStartsWith.class);

        matchersMockedStatic.when(() -> Matchers.startsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWith_returnsEndsWithMatcher() {
        final var stringMatcher = buildStringMatcher("ending with \"foo\"", StringEndsWith.class);

        matchersMockedStatic.when(() -> Matchers.endsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWithIgnoringCase_returnsEndsWithIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("ending with \"foo\" case-insensitive", StringEndsWith.class);

        matchersMockedStatic.when(() -> Matchers.endsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContains_returnsContainsStringMatcher() {
        final var stringMatcher = buildStringMatcher("containing \"foo\"", StringContains.class);

        matchersMockedStatic.when(() -> Matchers.containsString("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsIgnoringCase_returnsContainsStringIgnoringCaseMatcher() {
        final var stringMatcher = buildStringMatcher("containing \"foo\" case-insensitive", StringContains.class);

        matchersMockedStatic.when(() -> Matchers.containsStringIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsInOrder_returnsStringContainsInOrderMatcher() {
        final var stringMatcher = buildStringMatcher("containing stream \"foo\", \"foo2\" in order",
                StringContainsInOrder.class);

        matchersMockedStatic.when(() -> Matchers.stringContainsInOrder(List.of("foo", "foo2"))).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher);

        assertEquals(matcherMock, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { -1, 0, 0.5, Integer.MAX_VALUE + 1D, -5, -0.1 })
    void getHamcrestStringMatcher_handlesStringWithLengthWithInvalidInteger_throwsSaplTestException(
            double returnValue) {
        final var stringMatcherMock = mock(StringWithLength.class);
        when(stringMatcherMock.getLength()).thenReturn(BigDecimal.valueOf(returnValue));

        final var exception = assertThrows(SaplTestException.class,
                () -> stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock));

        assertEquals("String length needs to be an natural number larger than 0", exception.getMessage());
        assertInstanceOf(ArithmeticException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, Integer.MAX_VALUE, 555 })
    void getHamcrestStringMatcher_handlesStringWithLengthWithValidInteger_returnsHasLengthMatcher(int returnValue) {
        final var stringMatcherMock = mock(StringWithLength.class);
        when(stringMatcherMock.getLength()).thenReturn(BigDecimal.valueOf(returnValue));

        matchersMockedStatic.when(() -> Matchers.hasLength(returnValue)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }
}
