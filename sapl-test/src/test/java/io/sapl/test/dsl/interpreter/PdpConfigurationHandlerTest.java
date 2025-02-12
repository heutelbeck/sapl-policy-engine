/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.CombiningAlgorithmEnum;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.integration.SaplIntegrationTestFixture;

@ExtendWith(MockitoExtension.class)
class PdpConfigurationHandlerTest {

    @Mock
    ValueInterpreter              valueInterpreterMock;
    @Mock
    CombiningAlgorithmInterpreter combiningAlgorithmInterpreterMock;

    @InjectMocks
    PdpConfigurationHandler pdpConfigurationHandler;

    @Mock
    SaplIntegrationTestFixture saplIntegrationTestFixtureMock;

    @Mock
    SaplIntegrationTestFixture adjustedSaplIntegrationTestFixtureMock;

    private PdpVariables buildPdpVariables(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getPdpVariablesRule, PdpVariables.class);
    }

    private PdpCombiningAlgorithm buildPdpCombiningAlgorithm(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getPdpCombiningAlgorithmRule,
                PdpCombiningAlgorithm.class);
    }

    @Test
    void applyPDPConfigurationToFixture_handlesCombiningAlgorithmBeingDefined_returnsSaplIntegrationTestFixture() {
        final var pdpCombiningAlgorithm = buildPdpCombiningAlgorithm("pdp combining-algorithm only-one-applicable");

        final var pdpCombiningAlgorithmMock = mock(PolicyDocumentCombiningAlgorithm.class);
        when(combiningAlgorithmInterpreterMock
                .interpretPdpCombiningAlgorithm(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE))
                .thenReturn(pdpCombiningAlgorithmMock);

        when(saplIntegrationTestFixtureMock.withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock))
                .thenReturn(adjustedSaplIntegrationTestFixtureMock);

        final var result = pdpConfigurationHandler.applyPdpConfigurationToFixture(saplIntegrationTestFixtureMock, null,
                pdpCombiningAlgorithm);

        assertEquals(adjustedSaplIntegrationTestFixtureMock, result);

        verify(saplIntegrationTestFixtureMock, times(1)).withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock);

        verifyNoMoreInteractions(saplIntegrationTestFixtureMock);
    }

    @Test
    void applyPDPConfigurationToFixture_handlesCombiningAlgorithmBeingNotDefined_returnsSaplIntegrationTestFixture() {
        final var pdpCombiningAlgorithmMock = mock(PdpCombiningAlgorithm.class);

        when(pdpCombiningAlgorithmMock.isCombiningAlgorithmDefined()).thenReturn(false);

        final var result = pdpConfigurationHandler.applyPdpConfigurationToFixture(saplIntegrationTestFixtureMock, null,
                pdpCombiningAlgorithmMock);

        assertEquals(saplIntegrationTestFixtureMock, result);

        verifyNoInteractions(saplIntegrationTestFixtureMock);
    }

    @Test
    void applyPDPConfigurationToFixture_handlesWrongPdpVariablesType_returnsSaplIntegrationTestFixture() {
        final var pdpVariablesMock = mock(PdpVariables.class);

        final var value = ParserUtil.buildStringLiteral("\"ABC\"");
        when(pdpVariablesMock.getPdpVariables()).thenReturn(value);

        final var result = pdpConfigurationHandler.applyPdpConfigurationToFixture(saplIntegrationTestFixtureMock,
                pdpVariablesMock, null);

        assertEquals(saplIntegrationTestFixtureMock, result);

        verifyNoInteractions(saplIntegrationTestFixtureMock);
    }

    @Test
    void applyPDPConfigurationToFixture_handlesMultiplePdpVariables_returnsSaplIntegrationTestFixture() {
        final var pdpVariables = buildPdpVariables("pdp variables { \"foo\" : \"bar\" }");

        final var pdpEnvironmentVariablesMock = Collections.<String, Val>emptyMap();
        when(valueInterpreterMock.destructureObject(any())).thenAnswer(invocationOnMock -> {
            final io.sapl.test.grammar.sapltest.Object environment = invocationOnMock.getArgument(0);

            assertEquals(1, environment.getMembers().size());
            final var pair = environment.getMembers().get(0);
            assertEquals("foo", pair.getKey());
            assertEquals("bar", ((StringLiteral) pair.getValue()).getString());
            return pdpEnvironmentVariablesMock;
        });

        when(saplIntegrationTestFixtureMock.withPDPVariables(pdpEnvironmentVariablesMock))
                .thenReturn(adjustedSaplIntegrationTestFixtureMock);

        final var result = pdpConfigurationHandler.applyPdpConfigurationToFixture(saplIntegrationTestFixtureMock,
                pdpVariables, null);

        assertEquals(adjustedSaplIntegrationTestFixtureMock, result);

        verify(saplIntegrationTestFixtureMock, times(1)).withPDPVariables(pdpEnvironmentVariablesMock);
    }
}
