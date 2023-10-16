package io.sapl.test.dsl.interpreter.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.*;
import java.math.BigDecimal;
import java.util.List;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringMatcherInterpreterTest {

    @Mock
    Matcher<? super String> matcherMock;
    MockedStatic<Matchers> matchersMockedStatic = mockStatic(Matchers.class);

    private StringMatcherInterpreter stringMatcherInterpreter;

    @BeforeEach
    void setUp() {
        stringMatcherInterpreter = new StringMatcherInterpreter();
    }

    @AfterEach
    void tearDown() {
        matchersMockedStatic.close();
    }

    @Test
    void getHamcrestStringMatcher_handlesNullStringMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> stringMatcherInterpreter.getHamcrestStringMatcher(null));

        assertEquals("Unknown type of StringMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestStringMatcher_handlesUnknownStringMatcher_throwsSaplTestException() {
        final var stringMatcherMock = mock(StringMatcher.class);

        final var exception = assertThrows(SaplTestException.class, () -> stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock));

        assertEquals("Unknown type of StringMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNull_returnsNullValueMatcher() {
        final var stringMatcherMock = mock(StringIsNull.class);

        matchersMockedStatic.when(() -> Matchers.nullValue(String.class)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsBlank_returnsBlankStringMatcher() {
        final var stringMatcherMock = mock(StringIsBlank.class);

        matchersMockedStatic.when(Matchers::blankString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEmpty_returnsEmptyStringMatcher() {
        final var stringMatcherMock = mock(StringIsEmpty.class);

        matchersMockedStatic.when(Matchers::emptyString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrEmpty_returnsEmptyOrNullStringMatcher() {
        final var stringMatcherMock = mock(StringIsNullOrEmpty.class);

        matchersMockedStatic.when(Matchers::emptyOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsNullOrBlank_returnsBlankOrNullStringMatcher() {
        final var stringMatcherMock = mock(StringIsNullOrBlank.class);

        matchersMockedStatic.when(Matchers::blankOrNullString).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualWithCompressedWhiteSpace_returnsEqualToCompressingWhiteSpaceMatcher() {
        final var stringMatcherMock = mock(StringIsEqualWithCompressedWhiteSpace.class);
        when(stringMatcherMock.getValue()).thenReturn("foo");

        matchersMockedStatic.when(() -> Matchers.equalToCompressingWhiteSpace("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringIsEqualIgnoringCase_returnsEqualToIgnoringCaseMatcher() {
        final var stringMatcherMock = mock(StringIsEqualIgnoringCase.class);
        when(stringMatcherMock.getValue()).thenReturn("foo");

        matchersMockedStatic.when(() -> Matchers.equalToIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringMatchesRegex_returnsMatchesRegexMatcher() {
        final var stringMatcherMock = mock(StringMatchesRegex.class);
        when(stringMatcherMock.getRegex()).thenReturn("foo");

        matchersMockedStatic.when(() -> Matchers.matchesRegex("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWith_returnsStartsWithMatcher() {
        final var stringMatcherMock = mock(StringStartsWith.class);
        when(stringMatcherMock.getStart()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(false);

        matchersMockedStatic.when(() -> Matchers.startsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringStartsWithIgnoringCase_returnsStartsWithIgnoringCaseMatcher() {
        final var stringMatcherMock = mock(StringStartsWith.class);
        when(stringMatcherMock.getStart()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(true);

        matchersMockedStatic.when(() -> Matchers.startsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWith_returnsEndsWithMatcher() {
        final var stringMatcherMock = mock(StringEndsWith.class);
        when(stringMatcherMock.getEnd()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(false);

        matchersMockedStatic.when(() -> Matchers.endsWith("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringEndsWithIgnoringCase_returnsEndsWithIgnoringCaseMatcher() {
        final var stringMatcherMock = mock(StringEndsWith.class);
        when(stringMatcherMock.getEnd()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(true);

        matchersMockedStatic.when(() -> Matchers.endsWithIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContains_returnsContainsStringMatcher() {
        final var stringMatcherMock = mock(StringContains.class);
        when(stringMatcherMock.getValue()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(false);

        matchersMockedStatic.when(() -> Matchers.containsString("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsIgnoringCase_returnsContainsStringIgnoringCaseMatcher() {
        final var stringMatcherMock = mock(StringContains.class);
        when(stringMatcherMock.getValue()).thenReturn("foo");
        when(stringMatcherMock.isCaseInsensitive()).thenReturn(true);

        matchersMockedStatic.when(() -> Matchers.containsStringIgnoringCase("foo")).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringContainsInOrder_returnsStringContainsInOrderMatcher() {
        final var stringMatcherMock = mock(StringContainsInOrder.class);
        final var substrings = Helper.mockEList(List.of("a", "b"));
        when(stringMatcherMock.getSubstrings()).thenReturn(substrings);

        matchersMockedStatic.when(() -> Matchers.stringContainsInOrder(substrings)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestStringMatcher_handlesStringWithLength_returnsHasLengthMatcher() {
        final var stringMatcherMock = mock(StringWithLength.class);
        when(stringMatcherMock.getLength()).thenReturn(BigDecimal.ONE);

        matchersMockedStatic.when(() -> Matchers.hasLength(1)).thenReturn(matcherMock);

        final var result = stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcherMock);

        assertEquals(matcherMock, result);
    }
}