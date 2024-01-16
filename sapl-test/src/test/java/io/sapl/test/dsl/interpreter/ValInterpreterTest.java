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

import static io.sapl.test.dsl.ParserUtil.buildValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.Val;
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.Array;
import io.sapl.test.grammar.sAPLTest.Pair;
import io.sapl.test.grammar.sAPLTest.Value;
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

@ExtendWith(MockitoExtension.class)
class ValInterpreterTest {
    @Mock
    private ObjectMapper   objectMapperMock;
    @Mock
    private Val            valMock;
    @InjectMocks
    private ValInterpreter valInterpreter;

    private final MockedStatic<Val> valMockedStatic = mockStatic(Val.class);

    @AfterEach
    void tearDown() {
        valMockedStatic.close();
    }

    @Nested
    @DisplayName("Get val from value")
    class getValFromValueTest {

        @Test
        void getValFromValue_handlesNull_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> valInterpreter.getValFromValue(null));

            assertEquals("Unknown type of Value", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesUnknownValue_throwsSaplTestException() {
            final var valueMock = mock(Value.class);

            final var exception = assertThrows(SaplTestException.class,
                    () -> valInterpreter.getValFromValue(valueMock));

            assertEquals("Unknown type of Value", exception.getMessage());
        }

        @Test
        void getValFromValue_handlesNumberLiteral_returnsBigDecimalVal() {
            final var value = buildValue("5");

            valMockedStatic.when(() -> Val.of(BigDecimal.valueOf(5))).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesStringLiteral_returnsTextVal() {
            final var value = buildValue("\"foo\"");

            valMockedStatic.when(() -> Val.of("foo")).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesFalseLiteral_returnsBooleanVal() {
            final var value = buildValue("false");

            valMockedStatic.when(() -> Val.of(false)).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(io.sapl.api.interpreter.Val.of(false), result);
        }

        @Test
        void getValFromValue_handlesTrueLiteral_returnsBooleanVal() {
            final var value = buildValue("true");

            valMockedStatic.when(() -> Val.of(true)).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(io.sapl.api.interpreter.Val.of(true), result);
        }

        @Test
        void getValFromValue_handlesNullLiteral_returnsNullVal() {
            final var value = buildValue("null");

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(Val.NULL, result);
        }

        @Test
        void getValFromValue_handlesUndefinedLiteral_returnsUndefinedVal() {
            final var value = buildValue("undefined");

            final var result = valInterpreter.getValFromValue(value);

            assertEquals(Val.UNDEFINED, result);
        }

        @Nested
        @DisplayName("Array tests")
        class ArrayTest {
            @Test
            void getValFromValue_handlesArrayWithNullItems_returnsEmptyArrayVal() {
                final var valueMock = mock(Array.class);

                when(valueMock.getItems()).thenReturn(null);

                valMockedStatic.when(Val::ofEmptyArray).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesArrayWithEmptyItems_returnsEmptyArrayVal() {
                final var valueMock = mock(Array.class);

                final var itemsMock = Helper.mockEList(List.<Value>of());
                when(valueMock.getItems()).thenReturn(itemsMock);

                valMockedStatic.when(Val::ofEmptyArray).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesArrayWithMultipleValues_returnsArrayVal() {
                final var value = buildValue("[10, true]");

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

                final var result = valInterpreter.getValFromValue(value);

                assertEquals(valMock, result);

                verify(arrayNodeMock, times(1)).addAll(List.of(jsonNodeWithNumberMock, jsonNodeWithBooleanMock));
            }
        }

        @Nested
        @DisplayName("Object tests")
        class ObjectTest {
            @Test
            void getValFromValue_handlesObjectWithNullMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

                when(valueMock.getMembers()).thenReturn(null);

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithEmptyMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

                final var itemsMock = Helper.mockEList(List.<Pair>of());
                when(valueMock.getMembers()).thenReturn(itemsMock);

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithMultipleValues_returnsObjectVal() {
                final var value = buildValue("{ \"numberLiteralKey\": 10, \"trueLiteralKey\": true}");

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

                final var result = valInterpreter.getValFromValue(value);

                assertEquals(valMock, result);

                verify(objectNodeMock, times(1)).setAll(
                        Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock));
            }
        }
    }

    @Nested
    @DisplayName("Destructure object tests")
    class DestructureObjectTest {
        @Test
        void destructureObject_handlesNullObject_returnsEmptyMap() {
            final var result = valInterpreter.destructureObject(null);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithNullMembers_returnsEmptyMap() {
            final var objectMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

            when(objectMock.getMembers()).thenReturn(null);

            final var result = valInterpreter.destructureObject(objectMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithEmptyMembers_returnsEmptyMap() {
            final var objectMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

            final var itemsMock = Helper.mockEList(List.<Pair>of());
            when(objectMock.getMembers()).thenReturn(itemsMock);

            final var result = valInterpreter.destructureObject(objectMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithMultipleValues_returnsMap() {
            final io.sapl.test.grammar.sAPLTest.Object object = buildValue(
                    "{ \"numberLiteralKey\": 10, \"trueLiteralKey\": true}");

            final var valWithNumberMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

            final var jsonNodeWithNumberMock = mock(JsonNode.class);
            when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

            final var valWithBooleanMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

            final var jsonNodeWithBooleanMock = mock(JsonNode.class);
            when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

            final var result = valInterpreter.destructureObject(object);

            assertEquals(Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock),
                    result);
        }
    }
}
