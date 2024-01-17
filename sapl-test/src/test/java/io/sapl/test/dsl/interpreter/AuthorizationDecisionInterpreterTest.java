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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionType;
import io.sapl.test.grammar.sAPLTest.NumberLiteral;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionInterpreterTest {
    @Mock
    private ValueInterpreter                 valueInterpreterMock;
    @Mock
    private ObjectMapper                     objectMapperMock;
    @InjectMocks
    private AuthorizationDecisionInterpreter authorizationDecisionInterpreter;

    private NumberLiteral buildNumberLiteral(final String input) {
        return ParserUtil.buildValue(input, NumberLiteral.class);
    }

    @Test
    void constructAuthorizationDecision_handlesNullAuthorizationDecisionType_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> authorizationDecisionInterpreter.constructAuthorizationDecision(null, null, null, null));

        assertEquals("AuthorizationDecisionType is null", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("authorizationDecisionTypeToAuthorizationDecision")
    void constructAuthorizationDecision_shouldIgnoreNullResourceAndObligationsAndAdvice_returnsCorrectAuthorizationDecision(
            final AuthorizationDecisionType authorizationDecisionType,
            final AuthorizationDecision expectedAuthorizationDecision) {
        final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(authorizationDecisionType,
                null, null, null);

        assertEquals(expectedAuthorizationDecision, result);
    }

    private static Stream<Arguments> authorizationDecisionTypeToAuthorizationDecision() {
        return Stream.of(Arguments.of(AuthorizationDecisionType.PERMIT, AuthorizationDecision.PERMIT),
                Arguments.of(AuthorizationDecisionType.DENY, AuthorizationDecision.DENY),
                Arguments.of(AuthorizationDecisionType.INDETERMINATE, AuthorizationDecision.INDETERMINATE),
                Arguments.of(AuthorizationDecisionType.NOT_APPLICABLE, AuthorizationDecision.NOT_APPLICABLE));
    }

    @Nested
    @DisplayName("obligations and resource and advice mapping tests")
    class ObligationsAndResourceAndAdviceMappingTest {
        @Test
        void constructAuthorizationDecision_shouldInterpretResourceOnlyForEmptyObligationsAndAdvice_returnsCorrectAuthorizationDecision() {
            final var value = buildNumberLiteral("5");

            final var expectedVal = Val.of(5);
            when(valueInterpreterMock.getValFromValue(value)).thenReturn(expectedVal);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(
                    AuthorizationDecisionType.INDETERMINATE, value, Collections.emptyList(), Collections.emptyList());

            assertEquals(Decision.INDETERMINATE, result.getDecision());
            assertEquals(expectedVal.get(), result.getResource().get());
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceOnlyForNullObligationsAndAdvice_returnsCorrectAuthorizationDecision() {
            final var value = buildNumberLiteral("5");

            final var expectedVal = Val.of(5);

            when(valueInterpreterMock.getValFromValue(value)).thenReturn(expectedVal);

            final var result = authorizationDecisionInterpreter
                    .constructAuthorizationDecision(AuthorizationDecisionType.INDETERMINATE, value, null, null);

            assertEquals(Decision.INDETERMINATE, result.getDecision());
            assertEquals(expectedVal.get(), result.getResource().get());
        }

        @Test
        void constructAuthorizationDecision_shouldInterpretObligationsOnlyForNullResourceAndAdvice_returnsCorrectAuthorizationDecision() {
            final var value = buildNumberLiteral("5");

            final var expectedVal = Val.of(5);

            when(valueInterpreterMock.getValFromValue(value)).thenReturn(expectedVal);

            final var obligationsMock = mock(ArrayNode.class);
            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock);

            final var result = authorizationDecisionInterpreter
                    .constructAuthorizationDecision(AuthorizationDecisionType.DENY, null, List.of(value), null);

            assertEquals(Decision.DENY, result.getDecision());
            assertEquals(obligationsMock, result.getObligations().get());

            verify(obligationsMock, times(1)).add(expectedVal.get());

        }

        @Test
        void constructAuthorizationDecision_shouldInterpretResourceAndObligationsAndAdvice_returnsCorrectAuthorizationDecision() {
            final var resourceVal = Val.of(1);

            final var resourceValue = buildNumberLiteral("1");
            when(valueInterpreterMock.getValFromValue(resourceValue)).thenReturn(resourceVal);

            final var obligationValue1 = buildNumberLiteral("2");
            final var obligationValue2 = buildNumberLiteral("3");

            final var adviceValue1 = buildNumberLiteral("4");
            final var adviceValue2 = buildNumberLiteral("5");

            final var expectedObligation1Val = Val.of(2);
            final var expectedObligation2Val = Val.of(3);

            final var expectedAdvice1Val = Val.of(4);
            final var expectedAdvice2Val = Val.of(5);

            when(valueInterpreterMock.getValFromValue(obligationValue1)).thenReturn(expectedObligation1Val);
            when(valueInterpreterMock.getValFromValue(obligationValue2)).thenReturn(expectedObligation2Val);

            when(valueInterpreterMock.getValFromValue(adviceValue1)).thenReturn(expectedAdvice1Val);
            when(valueInterpreterMock.getValFromValue(adviceValue2)).thenReturn(expectedAdvice2Val);

            final var obligationsMock = mock(ArrayNode.class);

            final var adviceMock = mock(ArrayNode.class);

            when(objectMapperMock.createArrayNode()).thenReturn(obligationsMock).thenReturn(adviceMock);

            final var result = authorizationDecisionInterpreter.constructAuthorizationDecision(
                    AuthorizationDecisionType.NOT_APPLICABLE, resourceValue,
                    List.of(obligationValue1, obligationValue2), List.of(adviceValue1, adviceValue2));

            assertEquals(Decision.NOT_APPLICABLE, result.getDecision());
            assertEquals(resourceVal.get(), result.getResource().get());
            assertEquals(obligationsMock, result.getObligations().get());
            assertEquals(adviceMock, result.getAdvice().get());

            verify(obligationsMock, times(1)).add(expectedObligation1Val.get());
            verify(obligationsMock, times(1)).add(expectedObligation2Val.get());

            verify(adviceMock, times(1)).add(expectedAdvice1Val.get());
            verify(adviceMock, times(1)).add(expectedAdvice2Val.get());
        }
    }
}
