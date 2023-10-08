package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.NumberLiteral;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.grammar.sAPLTest.Value;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValInterpreterTest {

    private ObjectMapper objectMapperMock;

    private ValInterpreter valInterpreter;

    @BeforeEach
    void setUp() {
        objectMapperMock = mock(ObjectMapper.class);
        valInterpreter = new ValInterpreter(objectMapperMock);
    }

    @Nested
    @DisplayName("Get val from return value")
    class getValFromReturnValueTests {

        @Test
        void getValFromReturnValue_handlesUnknownVal_returnsNull() {
            final var valMock = mock(Value.class);
            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertNull(result);
        }

        @Test
        void getValFromReturnValue_handlesIntVal_returnsSaplVal() {
            final var valMock = mock(NumberLiteral.class);
            when(valMock.getNumber()).thenReturn(BigDecimal.valueOf(5));

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of(5), result);
        }

        @Test
        void getValFromReturnValue_handlesStringVal_returnsSaplVal() {
            final var valMock = mock(StringLiteral.class);
            when(valMock.getString()).thenReturn("foo");

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of("foo"), result);
        }

        @Test
        void getValFromReturnValue_handlesFalseLiteral_returnsSaplVal() {
            final var valMock = mock(FalseLiteral.class);

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of(false), result);
        }

        @Test
        void getValFromReturnValue_handlesTrueLiteral_returnsSaplVal() {
            final var valMock = mock(TrueLiteral.class);

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of(true), result);
        }
    }


    @Nested
    @DisplayName("Get val matcher from val")
    class getValMatcherFromVal {
        @Test
        void getValMatcherFromVal_handlesUnknownVal_returnsNull() {
            final var valMatcherMock = mock(ValMatcher.class);
            final var result = valInterpreter.getValMatcherFromVal(valMatcherMock);

            assertNull(result);
        }

        @Test
        void getValMatcherFromVal_handlesIntVal_returnsExpectedMatcherForSaplVal() {
            final var valMatcherMock = mock(ValWithValue.class);
            final var valMock = mock(NumberLiteral.class);

            when(valMatcherMock.getValue()).thenReturn(valMock);
            when(valMock.getNumber()).thenReturn(BigDecimal.valueOf(5));

            final var result = valInterpreter.getValMatcherFromVal(valMatcherMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of(5)));
        }

        @Test
        void getValMatcherFromVal_handlesStringVal_returnsExpectedMatcherForSaplVal() {
            final var valMatcherMock = mock(ValWithValue.class);
            final var valMock = mock(StringLiteral.class);

            when(valMatcherMock.getValue()).thenReturn(valMock);
            when(valMock.getString()).thenReturn("foo");

            final var result = valInterpreter.getValMatcherFromVal(valMatcherMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of("foo")));
        }

        @Test
        void getValMatcherFromVal_handlesFalseLiteral_returnsExpectedMatcherForSaplVal() {
            final var valMatcherMock = mock(ValWithValue.class);
            final var valMock = mock(FalseLiteral.class);

            when(valMatcherMock.getValue()).thenReturn(valMock);

            final var result = valInterpreter.getValMatcherFromVal(valMatcherMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of(false)));
        }

        @Test
        void getValMatcherFromVal_handlesTrueLiteral_returnsExpectedMatcherForSaplVal() {
            final var valMatcherMock = mock(ValWithValue.class);
            final var valMock = mock(TrueLiteral.class);

            when(valMatcherMock.getValue()).thenReturn(valMock);

            final var result = valInterpreter.getValMatcherFromVal(valMatcherMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of(true)));
        }
    }
}