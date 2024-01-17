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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.StringIsNull;
import io.sapl.test.grammar.sAPLTest.StringOrStringMatcher;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithError;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValMatcherInterpreterTests {
    @Mock
    private ValueInterpreter           valueInterpreterMock;
    @Mock
    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreterMock;
    @Mock
    private StringMatcherInterpreter   stringMatcherInterpreterMock;
    @InjectMocks
    private ValMatcherInterpreter      matcherInterpreter;

    @Mock
    Matcher<JsonNode> jsonNodeMatcherMock;
    @Mock
    Matcher<String>   stringMatcherMock;

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

    private <T extends ValMatcher> T buildValMatcher(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getValMatcherRule, clazz);
    }

    @Test
    void getHamcrestValMatcher_forNullValMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(null));

        assertEquals("Unknown type of ValMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestValMatcher_forUnknownValMatcher_throwsSaplTestException() {
        final var unknownValMatcherMock = mock(ValMatcher.class);

        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(unknownValMatcherMock));

        assertEquals("Unknown type of ValMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestValMatcher_forValWithValueAndNullMappedVal_throwsSaplTestException() {
        final var valMatcher = buildValMatcher("\"5\"", ValWithValue.class);

        when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(valMatcher));

        assertEquals("Val is null", exception.getMessage());
    }

    @Test
    void getHamcrestValMatcher_forValWithValue_returnsIsMatcher() {
        final var valMatcher = buildValMatcher("\"5\"", ValWithValue.class);

        final var expectedVal = Val.of(5);

        when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("5"))).thenReturn(expectedVal);

        final var isMatcherMock = mock(Matcher.class);
        hamcrestCoreMatchersMockedStatic.when(() -> CoreMatchers.is(expectedVal)).thenReturn(isMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(isMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forAnyVal_returnsAnyValMatcher() {
        final var valMatcher = buildValMatcher("any", AnyVal.class);

        final var anyValMatcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::anyVal).thenReturn(anyValMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(anyValMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithMatcherAndNullMappedMatcher_throwsSaplTestException() {
        final var valMatcher = buildValMatcher("matching null", ValWithMatcher.class);

        when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class))).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(valMatcher));

        assertEquals("JsonNodeMatcher is null", exception.getMessage());
    }

    @Test
    void getHamcrestValMatcher_forValWithMatcher_returnsValMatcher() {
        final var valMatcher = buildValMatcher("matching null", ValWithMatcher.class);

        when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(any(IsJsonNull.class)))
                .thenReturn(jsonNodeMatcherMock);

        final var valWithJsonNodeMatcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.val(jsonNodeMatcherMock))
                .thenReturn(valWithJsonNodeMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(valWithJsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorWithNullMatcher_returnsAnyValErrorMatcher() {
        final var valMatcher = buildValMatcher("with error", ValWithError.class);

        final var valErrorMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::valError).thenReturn(valErrorMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(valErrorMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorWithStringMatcherAndNullMappedMatcher_throwsSaplTestException() {
        final var valMatcher = buildValMatcher("with error null", ValWithError.class);

        when(stringMatcherInterpreterMock.getHamcrestStringMatcher(any(StringIsNull.class))).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(valMatcher));

        assertEquals("StringMatcher is null", exception.getMessage());
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorWithStringMatcher_returnsValErrorWithStringMatcher() {
        final var valMatcher = buildValMatcher("with error null", ValWithError.class);

        doReturn(stringMatcherMock).when(stringMatcherInterpreterMock)
                .getHamcrestStringMatcher(any(StringIsNull.class));

        final var valErrorWithMatcher = mock(Matcher.class);

        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.valError(stringMatcherMock))
                .thenReturn(valErrorWithMatcher);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(valErrorWithMatcher, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorWithPlainString_returnsValErrorMatcherWithMessage() {
        final var valMatcher = buildValMatcher("with error \"foo\"", ValWithError.class);

        final var valErrorMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.valError("foo")).thenReturn(valErrorMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valMatcher);

        assertEquals(valErrorMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorWithUnknownStringOrStringMatcher_throwsSaplTestException() {
        final var valMatcherMock                   = mock(ValWithError.class);
        final var unknownStringOrStringMatcherMock = mock(StringOrStringMatcher.class);

        when(valMatcherMock.getError()).thenReturn(unknownStringOrStringMatcherMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> matcherInterpreter.getHamcrestValMatcher(valMatcherMock));

        assertEquals("Unknown type of StringOrStringMatcher", exception.getMessage());
    }
}
