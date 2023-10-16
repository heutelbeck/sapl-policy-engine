package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValInterpreterTest {

    @Mock
    private ObjectMapper objectMapperMock;

    @Mock
    private Val valMock;

    MockedStatic<Val> valMockedStatic = mockStatic(Val.class);

    private ValInterpreter valInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreter = new ValInterpreter(objectMapperMock);
    }

    @AfterEach
    void tearDown() {
        valMockedStatic.close();
    }

    @Nested
    @DisplayName("Get val from value")
    class getValFromValueTest {

        @Test
        void getValFromValue_handlesUnknownValue_returnsNull() {
            final var valueMock = mock(Value.class);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertNull(result);
        }

        @Test
        void getValFromValue_handlesNumberLiteral_returnsBigDecimalVal() {
            final var valueMock = mock(NumberLiteral.class);
            when(valueMock.getNumber()).thenReturn(BigDecimal.valueOf(5));

            valMockedStatic.when(() -> Val.of(BigDecimal.valueOf(5))).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesStringLiteral_returnsTextVal() {
            final var valueMock = mock(StringLiteral.class);
            when(valueMock.getString()).thenReturn("foo");

            valMockedStatic.when(() -> Val.of("foo")).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertEquals(valMock, result);
        }

        @Test
        void getValFromValue_handlesFalseLiteral_returnsBooleanVal() {
            final var valueMock = mock(FalseLiteral.class);

            valMockedStatic.when(() -> Val.of(false)).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertEquals(io.sapl.api.interpreter.Val.of(false), result);
        }

        @Test
        void getValFromValue_handlesTrueLiteral_returnsBooleanVal() {
            final var valueMock = mock(TrueLiteral.class);

            valMockedStatic.when(() -> Val.of(true)).thenReturn(valMock);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertEquals(io.sapl.api.interpreter.Val.of(true), result);
        }

        @Test
        void getValFromValue_handlesNullLiteral_returnsNullVal() {
            final var valueMock = mock(NullLiteral.class);

            final var result = valInterpreter.getValFromValue(valueMock);

            assertEquals(Val.NULL, result);
        }

        @Test
        void getValFromValue_handlesUndefinedLiteral_returnsUndefinedVal() {
            final var valueMock = mock(UndefinedLiteral.class);

            final var result = valInterpreter.getValFromValue(valueMock);

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
                final var valueMock = mock(Array.class);

                final var numberLiteralMock = mock(NumberLiteral.class);
                final var trueLiteralMock = mock(TrueLiteral.class);

                final var itemsMock = Helper.mockEList(List.of(numberLiteralMock, trueLiteralMock));
                when(valueMock.getItems()).thenReturn(itemsMock);

                when(numberLiteralMock.getNumber()).thenReturn(BigDecimal.TEN);

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

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);

                verify(arrayNodeMock, times(1)).addAll(List.of(jsonNodeWithNumberMock, jsonNodeWithBooleanMock));
            }
        }

        @Nested
        @DisplayName("Object tests")
        class ObjectTest {
            @Test
            void getValFromValue_handlesObjectWithNullMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(Object.class);

                when(valueMock.getMembers()).thenReturn(null);

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithEmptyMembers_returnsEmptyObjectVal() {
                final var valueMock = mock(Object.class);

                final var itemsMock = Helper.mockEList(List.<Pair>of());
                when(valueMock.getMembers()).thenReturn(itemsMock);

                valMockedStatic.when(Val::ofEmptyObject).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);
            }

            @Test
            void getValFromValue_handlesObjectWithMultipleValues_returnsObjectVal() {
                final var valueMock = mock(Object.class);

                final var numberLiteralMock = mock(NumberLiteral.class);
                final var trueLiteralMock = mock(TrueLiteral.class);

                final var numberLiteralPairMock = mock(Pair.class);
                final var trueLiteralPairMock = mock(Pair.class);

                final var itemsMock = Helper.mockEList(List.of(numberLiteralPairMock, trueLiteralPairMock));
                when(valueMock.getMembers()).thenReturn(itemsMock);

                when(numberLiteralPairMock.getKey()).thenReturn("numberLiteralKey");
                when(numberLiteralPairMock.getValue()).thenReturn(numberLiteralMock);

                when(numberLiteralMock.getNumber()).thenReturn(BigDecimal.TEN);

                final var valWithNumberMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

                final var jsonNodeWithNumberMock = mock(JsonNode.class);
                when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

                when(trueLiteralPairMock.getKey()).thenReturn("trueLiteralKey");
                when(trueLiteralPairMock.getValue()).thenReturn(trueLiteralMock);

                final var valWithBooleanMock = mock(Val.class);
                valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

                final var jsonNodeWithBooleanMock = mock(JsonNode.class);
                when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

                final var objectNodeMock = mock(ObjectNode.class);
                when(objectMapperMock.createObjectNode()).thenReturn(objectNodeMock);

                valMockedStatic.when(() -> Val.of(objectNodeMock)).thenReturn(valMock);

                final var result = valInterpreter.getValFromValue(valueMock);

                assertEquals(valMock, result);

                verify(objectNodeMock, times(1)).setAll(Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock));
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
            final var valueMock = mock(Object.class);

            when(valueMock.getMembers()).thenReturn(null);

            final var result = valInterpreter.destructureObject(valueMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithEmptyMembers_returnsEmptyMap() {
            final var valueMock = mock(Object.class);

            final var itemsMock = Helper.mockEList(List.<Pair>of());
            when(valueMock.getMembers()).thenReturn(itemsMock);

            final var result = valInterpreter.destructureObject(valueMock);

            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        void destructureObject_handlesObjectWithMultipleValues_returnsMap() {
            final var valueMock = mock(Object.class);

            final var numberLiteralMock = mock(NumberLiteral.class);
            final var trueLiteralMock = mock(TrueLiteral.class);

            final var numberLiteralPairMock = mock(Pair.class);
            final var trueLiteralPairMock = mock(Pair.class);

            final var itemsMock = Helper.mockEList(List.of(numberLiteralPairMock, trueLiteralPairMock));
            when(valueMock.getMembers()).thenReturn(itemsMock);

            when(numberLiteralPairMock.getKey()).thenReturn("numberLiteralKey");
            when(numberLiteralPairMock.getValue()).thenReturn(numberLiteralMock);

            when(numberLiteralMock.getNumber()).thenReturn(BigDecimal.TEN);

            final var valWithNumberMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(BigDecimal.TEN)).thenReturn(valWithNumberMock);

            final var jsonNodeWithNumberMock = mock(JsonNode.class);
            when(valWithNumberMock.get()).thenReturn(jsonNodeWithNumberMock);

            when(trueLiteralPairMock.getKey()).thenReturn("trueLiteralKey");
            when(trueLiteralPairMock.getValue()).thenReturn(trueLiteralMock);

            final var valWithBooleanMock = mock(Val.class);
            valMockedStatic.when(() -> Val.of(true)).thenReturn(valWithBooleanMock);

            final var jsonNodeWithBooleanMock = mock(JsonNode.class);
            when(valWithBooleanMock.get()).thenReturn(jsonNodeWithBooleanMock);

            final var result = valInterpreter.destructureObject(valueMock);

            assertEquals(Map.of("numberLiteralKey", jsonNodeWithNumberMock, "trueLiteralKey", jsonNodeWithBooleanMock), result);
        }
    }
}