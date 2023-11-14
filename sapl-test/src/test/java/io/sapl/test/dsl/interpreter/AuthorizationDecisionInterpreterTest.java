package io.sapl.test.dsl.interpreter;

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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionInterpreterTest {
    @Mock
    private ValInterpreter valInterpreterMock;
    @Mock
    private ObjectMapper objectMapperMock;
    @InjectMocks
    private AuthorizationDecisionInterpreter authorizationDecisionInterpreter;

    @Nested
    @DisplayName("decision mapping tests")
    class DecisionMapping {
        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceAndAdviceForPermit_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, null, null, null);

            assertEquals(AuthorizationDecision.PERMIT, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceAndAdviceForDeny_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY, null, null, null);

            assertEquals(AuthorizationDecision.DENY, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceAndAdviceForIndeterminate_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE, null, null, null);

            assertEquals(AuthorizationDecision.INDETERMINATE, result);
        }

        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceAndAdviceForNotApplicable_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE, null, null, null);

            assertEquals(AuthorizationDecision.NOT_APPLICABLE, result);
        }
    }

    @Nested
    @DisplayName("obligations and resource mapping tests")
    class ObligationsAndResourceAndAdviceMapping {
        @Test
        void constructAuthorizationDecision_shouldIgnoreNullObligationsAndResourceAndAdvice_returnsCorrectAuthorizationDecision() {
            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.PERMIT, null, null, null);

            assertEquals(AuthorizationDecision.PERMIT, result);
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceOnlyForNullObligationsAndAdvice_returnsCorrectAuthorizationDecision() {
            final var valueMock = mock(Value.class);

            final var saplValMock = mock(Val.class);

            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(saplValMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(saplValMock.get()).thenReturn(jsonNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.INDETERMINATE, valueMock, null, null);

            assertEquals(Decision.INDETERMINATE, result.getDecision());
            assertEquals(saplValMock.get(), result.getResource().get());
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsOnlyForNullResourceAndAdvice_returnsCorrectAuthorizationDecision() {
            final var valueMock = mock(Value.class);

            final var saplValMock = mock(Val.class);

            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(saplValMock);

            final var obligationsMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(saplValMock.get()).thenReturn(jsonNodeMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.DENY, null, List.of(valueMock), null);

            assertEquals(Decision.DENY, result.getDecision());
            assertEquals(obligationsMock, result.getObligations().get());

            verify(obligationsMock, times(1)).add(jsonNodeMock);

        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceAndObligationsAndAdvice_returnsCorrectAuthorizationDecision() {
            final var resourceValMock = mock(Val.class);

            final var resourceJsonNodeMock = mock(JsonNode.class);
            when(resourceValMock.get()).thenReturn(resourceJsonNodeMock);

            final var resourceValueMock = mock(Value.class);
            when(valInterpreterMock.getValFromValue(resourceValueMock)).thenReturn(resourceValMock);

            final var obligationValueMock1 = mock(Value.class);
            final var obligationValueMock2 = mock(Value.class);

            final var obligationValMock1 = mock(Val.class);
            final var obligationValMock2 = mock(Val.class);

            final var adviceValueMock1 = mock(Value.class);
            final var adviceValueMock2 = mock(Value.class);

            final var adviceValMock1 = mock(Val.class);
            final var adviceValMock2 = mock(Val.class);

            when(valInterpreterMock.getValFromValue(obligationValueMock1)).thenReturn(obligationValMock1);
            when(valInterpreterMock.getValFromValue(obligationValueMock2)).thenReturn(obligationValMock2);

            when(valInterpreterMock.getValFromValue(adviceValueMock1)).thenReturn(adviceValMock1);
            when(valInterpreterMock.getValFromValue(adviceValueMock2)).thenReturn(adviceValMock2);

            final var obligationsMock = mock(ArrayNode.class);

            final var adviceMock = mock(ArrayNode.class);

            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock).thenReturn(adviceMock);

            final var obligationJsonNodeMock1 = mock(JsonNode.class);
            final var obligationJsonNodeMock2 = mock(JsonNode.class);

            when(obligationValMock1.get()).thenReturn(obligationJsonNodeMock1);
            when(obligationValMock2.get()).thenReturn(obligationJsonNodeMock2);

            final var adviceJsonNodeMock1 = mock(JsonNode.class);
            final var adviceJsonNodeMock2 = mock(JsonNode.class);

            when(adviceValMock1.get()).thenReturn(adviceJsonNodeMock1);
            when(adviceValMock2.get()).thenReturn(adviceJsonNodeMock2);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(io.sapl.test.grammar.sAPLTest.AuthorizationDecision.NOT_APPLICABLE, resourceValueMock, List.of(obligationValueMock1, obligationValueMock2), List.of(adviceValueMock1, adviceValueMock2));

            assertEquals(Decision.NOT_APPLICABLE, result.getDecision());
            assertEquals(resourceJsonNodeMock, result.getResource().get());
            assertEquals(obligationsMock, result.getObligations().get());
            assertEquals(adviceMock, result.getAdvice().get());

            verify(obligationsMock, times(1)).add(obligationJsonNodeMock1);
            verify(obligationsMock, times(1)).add(obligationJsonNodeMock2);

            verify(adviceMock, times(1)).add(adviceJsonNodeMock1);
            verify(adviceMock, times(1)).add(adviceJsonNodeMock1);
        }
    }
}