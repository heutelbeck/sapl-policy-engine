package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sAPLTest.BoolVal;
import io.sapl.test.grammar.sAPLTest.IntVal;
import io.sapl.test.grammar.sAPLTest.StringVal;
import io.sapl.test.grammar.sAPLTest.Val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValInterpreterTest {

    private ValInterpreter valInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreter = new ValInterpreter();
    }

    @Nested
    @DisplayName("Get val from return value")
    class getValFromReturnValueTests {

        @Test
        void getValFromReturnValue_handlesUnknownVal_returnsNull() {
            final var valMock = mock(Val.class);
            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertNull(result);
        }

        @Test
        void getValFromReturnValue_handlesIntVal_returnsSaplVal() {
            final var valMock = mock(IntVal.class);
            when(valMock.getValue()).thenReturn(5);

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of(5), result);
        }

        @Test
        void getValFromReturnValue_handlesStringVal_returnsSaplVal() {
            final var valMock = mock(StringVal.class);
            when(valMock.getValue()).thenReturn("foo");

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of("foo"), result);
        }

        @Test
        void getValFromReturnValue_handlesBoolVal_returnsSaplVal() {
            final var valMock = mock(BoolVal.class);
            when(valMock.isIsTrue()).thenReturn(false);

            final var result = valInterpreter.getValFromReturnValue(valMock);

            assertEquals(io.sapl.api.interpreter.Val.of(false), result);
        }
    }


    @Nested
    @DisplayName("Get val matcher from val")
    class getValMatcherFromVal {
        @Test
        void getValMatcherFromVal_handlesUnknownVal_returnsNull() {
            final var valMock = mock(Val.class);
            final var result = valInterpreter.getValMatcherFromVal(valMock);

            assertNull(result);
        }

        @Test
        void getValMatcherFromVal_handlesIntVal_returnsExpectedMatcherForSaplVal() {
            final var valMock = mock(IntVal.class);
            when(valMock.getValue()).thenReturn(5);

            final var result = valInterpreter.getValMatcherFromVal(valMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of(5)));
        }

        @Test
        void getValMatcherFromVal_handlesStringVal_returnsExpectedMatcherForSaplVal() {
            final var valMock = mock(StringVal.class);
            when(valMock.getValue()).thenReturn("foo");

            final var result = valInterpreter.getValMatcherFromVal(valMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of("foo")));
        }

        @Test
        void getValMatcherFromVal_handlesBoolVal_returnsExpectedMatcherForSaplVal() {
            final var valMock = mock(BoolVal.class);
            when(valMock.isIsTrue()).thenReturn(false);

            final var result = valInterpreter.getValMatcherFromVal(valMock);

            assertTrue(result.matches(io.sapl.api.interpreter.Val.of(false)));
        }
    }
}