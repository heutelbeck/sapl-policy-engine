package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.test.grammar.sAPLTest.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthorizationDecisionInterpreterTest {

    private ValInterpreter valInterpreterMock;
    private ObjectMapper objectMapperMock;
    private AuthorizationDecisionInterpreter authorizationDecisionInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        objectMapperMock = mock(ObjectMapper.class);
        authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(valInterpreterMock, objectMapperMock);
    }

    @Nested
    @DisplayName("decision mapping tests")
    class DecisionMapping {
        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceForPermit_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, null, null);

            assertEquals(AuthorizationDecision.PERMIT, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceForDeny_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY, null, null);

            assertEquals(AuthorizationDecision.DENY, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceForIndeterminate_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE, null, null);

            assertEquals(AuthorizationDecision.INDETERMINATE, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceForNotApplicable_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE, null, null);

            assertEquals(AuthorizationDecision.NOT_APPLICABLE, result);
        }
    }

    @Nested
    @DisplayName("obligations and resource mapping tests")
    class ObligationsAndResourceMapping {
        @Test
        void constructAuthorizationDecision_shouldIgnoreEmptyObligationsAndResource_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, null, null);

            assertEquals(AuthorizationDecision.PERMIT, result);
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsOnlyForMissingResource_returnsCorrectAuthorizationDecision() {
            final var valueMock = mock(Value.class);

            final var saplValMock = mock(Val.class);

            when(valInterpreterMock.getValFromReturnValue(valueMock)).thenReturn(saplValMock);

            final var obligationsMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(saplValMock.get()).thenReturn(jsonNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY, valueMock, null);

            assertEquals(Decision.DENY, result.getDecision());
            assertEquals(obligationsMock, result.getObligations().get());

            verify(obligationsMock, times(1)).add(jsonNodeMock);
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceOnlyForMissingObligations_returnsCorrectAuthorizationDecision() {
            final var valueMock = mock(Value.class);

            final var saplValMock = mock(Val.class);

            when(valInterpreterMock.getValFromReturnValue(valueMock)).thenReturn(saplValMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(saplValMock.get()).thenReturn(jsonNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE, null, valueMock);

            assertEquals(Decision.INDETERMINATE, result.getDecision());
            assertEquals(saplValMock.get(), result.getResource().get());
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsAndResource_returnsCorrectAuthorizationDecision() {
            final var obligationValueMock = mock(Value.class);
            final var resourceValueMock = mock(Value.class);

            final var saplValMock = mock(Val.class);
            final var saplVal2Mock = mock(Val.class);

            when(valInterpreterMock.getValFromReturnValue(obligationValueMock)).thenReturn(saplValMock);

            final var obligationsMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(saplValMock.get()).thenReturn(jsonNodeMock);

            when(valInterpreterMock.getValFromReturnValue(resourceValueMock)).thenReturn(saplVal2Mock);

            final var jsonNode2Mock = mock(JsonNode.class);
            when(saplVal2Mock.get()).thenReturn(jsonNode2Mock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE, obligationValueMock, resourceValueMock);

            assertEquals(Decision.NOT_APPLICABLE, result.getDecision());
            assertEquals(obligationsMock, result.getObligations().get());
            assertEquals(jsonNode2Mock, result.getResource().get());

            verify(obligationsMock, times(1)).add(jsonNodeMock);

        }
    }
}