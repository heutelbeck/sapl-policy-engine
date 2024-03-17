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

import static io.sapl.test.dsl.ParserUtil.buildStringLiteral;
import static io.sapl.test.dsl.ParserUtil.buildValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.ErrorValue;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.grammar.sapltest.Array;
import io.sapl.test.grammar.sapltest.FalseLiteral;
import io.sapl.test.grammar.sapltest.NullLiteral;
import io.sapl.test.grammar.sapltest.NumberLiteral;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.sapltest.TrueLiteral;
import io.sapl.test.grammar.sapltest.UndefinedLiteral;
import io.sapl.test.grammar.sapltest.Value;

@ExtendWith(MockitoExtension.class)
class ValueInterpreterTests {
    @Mock
    protected ObjectMapper     objectMapperMock;
    @Mock
    protected Val              valMock;
    @InjectMocks
    protected ValueInterpreter valueInterpreter;

    protected final MockedStatic<Val> valMockedStatic = mockStatic(Val.class);

    @AfterEach
    void tearDown() {
        valMockedStatic.close();
    }

    @Nested
    @DisplayName("Get val from value")
    class getValFromValueTests {

        private ErrorValue buildErrorValue(final String input) {
            return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getErrorValueRule, ErrorValue.class);
        }

        @Test
        void getValFromValue_handlesNull_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> valueInterpreter.getValFromValue(null));

            assertEquals("Unknown type of Value", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesUnknownValue_throwsSaplTestException() {
            final var valueMock = mock(Value.class);

            final var exception = assertThrows(SaplTestException.class,
                    () -> valueInterpreter.getValFromValue(valueMock));

            assertEquals("Unknown type of Value", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesNumberLiteralWithNullNumber_throwsSaplTestException() {
            final var numberLiteralMock = mock(NumberLiteral.class);

            when(numberLiteralMock.getNumber()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> valueInterpreter.getValFromValue(numberLiteralMock));

            assertEquals("Number is null", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesNumberLiteral_returnsBigDecimalVal() {
            final var value = buildValue("5", NumberLiteral.class);

            valMockedStatic.when(() -> Val.of(BigDecimal.valueOf(5))).thenReturn(valMock);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesStringLiteralWithNullString_throwsSaplTestException() {
            final var stringLiteralMock = mock(StringLiteral.class);

            when(stringLiteralMock.getString()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> valueInterpreter.getValFromValue(stringLiteralMock));

            assertEquals("String is null", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesStringLiteral_returnsTextVal() {
            final var value = buildStringLiteral("\"foo\"");

            valMockedStatic.when(() -> Val.of("foo")).thenReturn(valMock);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesFalseLiteral_returnsFalseVal() {
            final var value = buildValue("false", FalseLiteral.class);

            valMockedStatic.when(() -> Val.of(false)).thenReturn(valMock);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(io.sapl.api.interpreter.Val.of(false), result);
        }

        @Test
        void getValFromValue_handlesTrueLiteral_returnsTrueVal() {
            final var value = buildValue("true", TrueLiteral.class);

            valMockedStatic.when(() -> Val.of(true)).thenReturn(valMock);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(io.sapl.api.interpreter.Val.of(true), result);
        }

        @Test
        void getValFromValue_handlesNullLiteral_returnsNullVal() {
            final var value = buildValue("null", NullLiteral.class);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(Val.NULL, result);
        }

        @Test
        void getValFromValue_handlesUndefinedLiteral_returnsUndefinedVal() {
            final var value = buildValue("undefined", UndefinedLiteral.class);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(Val.UNDEFINED, result);
        }

        @Test
        void getValFromValue_handlesErrorValueWithoutMessage_returnsErrorVal() {
            final var value = ParserUtil.parseInputByRule("error", SAPLTestGrammarAccess::getErrorValueRule,
                    ErrorValue.class);

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(Val.error(null, null), result);
        }

        @Test
        void getValFromValue_handlesErrorValueWithMessage_returnsErrorValWithMessage() {
            final var value = buildErrorValue("error (\"foo\")");

            final var result = valueInterpreter.getValFromValue(value);

            assertEquals(Val.error(null, "foo"), result);
        }

        @Nested
        @DisplayName("Array tests")
        class ArrayTests {
            @Test
            void getValFromValue_handlesArrayWithNullItems_returnsEmptyArrayVal() {
                final var valueMock = mock(Array.class);

                when(valueMock.getItems()).thenReturn(null);

                valMockedStatic.when(Val::ofEmptyArray).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesArrayWithEmptyItems_returnsEmptyArrayVal() {
                final var valueMock = mock(Array.class);

                TestHelper.mockEListResult(valueMock::getItems, Collections.emptyList());

                valMockedStatic.when(Val::ofEmptyArray).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesArrayWithMultipleValues_returnsArrayVal() {
                final var value = buildValue("[10, true]", Array.class);

                final var valWithNumberMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

                final var jsonNodeWithNumberMock = mock(JsonNode.class);
                when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

                final var valWithBooleanMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

                final var jsonNodeWithBooleanMock = mock(JsonNode.class);
                when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

                final var arrayNodeMock = mock(ArrayNode.class);
                when(objectMapperMock.createArrayNode()).thenReturn(arrayNodeMock);

                valMockedStatic.when(() -> Val.of(arrayNodeMock)).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(value);

                assertEquals(valMock, result);

                verify(arrayNodeMock, times(1)).addAll(List.of(jsonNodeWithNumberMock, jsonNodeWithBooleanMock));
            }
        }

        @Nested
        @DisplayName("Object tests")
        class ObjectTests {
            @Test
            void getValFromValue_handlesObjectWithNullMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(io.sapl.test.grammar.sapltest.Object.class);

                when(valueMock.getMembers()).thenReturn(null);

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithEmptyMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(io.sapl.test.grammar.sapltest.Object.class);

                TestHelper.mockEListResult(valueMock::getMembers, Collections.emptyList());

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithMultipleValues_returnsObjectVal() {
                final var value = buildValue("{ \"numberLiteralKey\": 10, \"trueLiteralKey\": true}",
                        io.sapl.test.grammar.sapltest.Object.class);

                final var valWithNumberMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

                final var jsonNodeWithNumberMock = mock(JsonNode.class);
                when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

                final var valWithBooleanMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

                final var jsonNodeWithBooleanMock = mock(JsonNode.class);
                when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

                final var objectNodeMock = mock(ObjectNode.class);
                when(objectMapperMock.createObjectNode()).thenReturn(objectNodeMock);

                valMockedStatic.when(() -> Val.of(objectNodeMock)).thenReturn(valMock);

                final var result = valueInterpreter.getValFromValue(value);

                assertEquals(valMock, result);

                verify(objectNodeMock, times(1)).setAll(
                        Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock));
            }
        }
    }

    @Nested
    @DisplayName("Destructure object tests")
    class DestructureObjectTests {
        @Test
        void destructureObject_handlesNullObject_returnsEmptyMap() {
            final var result = valueInterpreter.destructureObject(null);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithNullMembers_returnsEmptyMap() {
            final var objectMock = mock(io.sapl.test.grammar.sapltest.Object.class);

            when(objectMock.getMembers()).thenReturn(null);

            final var result = valueInterpreter.destructureObject(objectMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithEmptyMembers_returnsEmptyMap() {
            final var objectMock = mock(io.sapl.test.grammar.sapltest.Object.class);

            TestHelper.mockEListResult(objectMock::getMembers, Collections.emptyList());

            final var result = valueInterpreter.destructureObject(objectMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithMultipleValues_returnsMap() {
            final var object = buildValue("{ \"numberLiteralKey\": 10, \"trueLiteralKey\": true}",
                    io.sapl.test.grammar.sapltest.Object.class);

            final var valWithNumberMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

            final var jsonNodeWithNumberMock = mock(JsonNode.class);
            when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

            final var valWithBooleanMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

            final var jsonNodeWithBooleanMock = mock(JsonNode.class);
            when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

            final var result = valueInterpreter.destructureObject(object);

            assertEquals(Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock),
                    result);
        }
    }
}
