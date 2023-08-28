package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.test.grammar.sAPLTest.JsonElement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthorizationDecisionInterpreterTest {

    private ObjectMapper objectMapperMock;
    private AuthorizationDecisionInterpreter authorizationDecisionInterpreter;

    @BeforeEach
    void setUp() {
        objectMapperMock = mock(ObjectMapper.class);
        authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(objectMapperMock);
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
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, List.of(), List.of());

            assertEquals(AuthorizationDecision.PERMIT, result);
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsOnlyForMissingResource_returnsCorrectAuthorizationDecision() {
            final var jsonElementMock = mock(JsonElement.class);
            final var jsonElement2Mock = mock(JsonElement.class);

            when(jsonElementMock.getKey()).thenReturn("key1");
            when(jsonElementMock.getValue()).thenReturn("value1");
            when(jsonElement2Mock.getKey()).thenReturn("key2");
            when(jsonElement2Mock.getValue()).thenReturn("value2");

            final var arrayNodeMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(arrayNodeMock);

            final var objectNodeMock = mock(ObjectNode.class);
            when(objectMapperMock.createObjectNode()).thenReturn(objectNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY, List.of(jsonElementMock, jsonElement2Mock), null);

            assertEquals(Decision.DENY, result.getDecision());
            assertEquals(arrayNodeMock, result.getObligations().get());

            verify(objectNodeMock, times(1)).put("key1", "value1");
            verify(objectNodeMock, times(1)).put("key2", "value2");
            verify(arrayNodeMock, times(1)).add(objectNodeMock);
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceOnlyForMissingObligations_returnsCorrectAuthorizationDecision() {
            final var jsonElementMock = mock(JsonElement.class);
            final var jsonElement2Mock = mock(JsonElement.class);

            when(jsonElementMock.getKey()).thenReturn("key1");
            when(jsonElementMock.getValue()).thenReturn("value1");
            when(jsonElement2Mock.getKey()).thenReturn("key2");
            when(jsonElement2Mock.getValue()).thenReturn("value2");

            final var objectNodeMock = mock(ObjectNode.class);
            when(objectMapperMock.createObjectNode()).thenReturn(objectNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE, List.of(), List.of(jsonElementMock, jsonElement2Mock));

            assertEquals(Decision.INDETERMINATE, result.getDecision());
            assertEquals(objectNodeMock, result.getResource().get());

            verify(objectNodeMock, times(1)).put("key1", "value1");
            verify(objectNodeMock, times(1)).put("key2", "value2");
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsAndResource_returnsCorrectAuthorizationDecision() {
            final var obligationElementMock = mock(JsonElement.class);
            final var resourceElementMock = mock(JsonElement.class);

            when(obligationElementMock.getKey()).thenReturn("key1");
            when(obligationElementMock.getValue()).thenReturn("value1");

            when(resourceElementMock.getKey()).thenReturn("key2");
            when(resourceElementMock.getValue()).thenReturn("value2");

            final var arrayNodeMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(arrayNodeMock);

            final var obligationObjectNodeMock = mock(ObjectNode.class);
            final var resourceObjectNodeMock = mock(ObjectNode.class);
            when(objectMapperMock.createObjectNode()).thenReturn(obligationObjectNodeMock).thenReturn(resourceObjectNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE, List.of(obligationElementMock), List.of(resourceElementMock));

            assertEquals(Decision.NOT_APPLICABLE, result.getDecision());
            assertEquals(arrayNodeMock, result.getObligations().get());
            assertEquals(resourceObjectNodeMock, result.getResource().get());

            verify(obligationObjectNodeMock, times(1)).put("key1", "value1");
            verify(resourceObjectNodeMock, times(1)).put("key2", "value2");
        }
    }
}