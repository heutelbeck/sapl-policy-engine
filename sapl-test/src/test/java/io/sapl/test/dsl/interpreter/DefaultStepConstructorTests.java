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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;

@ExtendWith(MockitoExtension.class)
class DefaultStepConstructorTests {
    @Mock
    protected DefaultTestFixtureConstructor defaultTestFixtureConstructorMock;
    @Mock
    protected DefaultTestCaseConstructor    defaultTestCaseConstructorMock;
    @Mock
    protected DefaultWhenStepConstructor    defaultWhenStepConstructorMock;
    @Mock
    protected DefaultExpectStepConstructor  defaultExpectStepConstructorMock;
    @Mock
    protected DefaultVerifyStepConstructor  defaultVerifyStepConstructorMock;
    @InjectMocks
    protected DefaultStepConstructor        defaultStepConstructor;

    @Test
    void constructTestFixture_callsTestFixtureConstructor_returnsSaplTestFixture() {
        final var documentMock              = mock(Document.class);
        final var pdpVariablesMock          = mock(PdpVariables.class);
        final var pdpCombiningAlgorithmMock = mock(PdpCombiningAlgorithm.class);
        final var givenSteps                = Collections.<GivenStep>emptyList();

        final var saplTestFixtureMock = mock(SaplTestFixture.class);

        final var registrations = new HashMap<ImportType, Map<String, Object>>();

        when(defaultTestFixtureConstructorMock.constructTestFixture(documentMock, pdpVariablesMock,
                pdpCombiningAlgorithmMock, givenSteps, registrations)).thenReturn(saplTestFixtureMock);

        final var result = defaultStepConstructor.constructTestFixture(documentMock, pdpVariablesMock,
                pdpCombiningAlgorithmMock, givenSteps, registrations);

        assertEquals(saplTestFixtureMock, result);
    }

    @Test
    void constructTestCase_callsTestCaseConstructorWithoutMocks_returnsGivenOrWhenStep() {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        final var environmentMock     = mock(Environment.class);
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestCaseConstructorMock.constructTestCase(saplTestFixtureMock, environmentMock, false))
                .thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.constructTestCase(saplTestFixtureMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void constructTestCase_callsTestCaseConstructorWithMocks_returnsGivenOrWhenStep() {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        final var environmentMock     = mock(Environment.class);
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestCaseConstructorMock.constructTestCase(saplTestFixtureMock, environmentMock, true))
                .thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.constructTestCase(saplTestFixtureMock, environmentMock, true);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void constructWhenStep_callsWhenStepConstructor_returnsWhenStep() {
        final var givenSteps          = Collections.<GivenStep>emptyList();
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        final var expectationMock     = mock(Expectation.class);

        final var whenStepMock = mock(WhenStep.class);

        when(defaultWhenStepConstructorMock.constructWhenStep(givenSteps, givenOrWhenStepMock, expectationMock))
                .thenReturn(whenStepMock);

        final var result = defaultStepConstructor.constructWhenStep(givenSteps, givenOrWhenStepMock, expectationMock);

        assertEquals(whenStepMock, result);
    }

    @Test
    void constructExpectStep_callsExpectStepConstructor_returnsExpectStep() {
        final var scenarioMock = mock(Scenario.class);
        final var whenStepMock = mock(WhenStep.class);

        final var expectStepMock = mock(ExpectStep.class);

        when(defaultExpectStepConstructorMock.constructExpectStep(scenarioMock, whenStepMock))
                .thenReturn(expectStepMock);

        final var result = defaultStepConstructor.constructExpectStep(scenarioMock, whenStepMock);

        assertEquals(expectStepMock, result);
    }

    @Test
    void constructVerifyStep_callsVerifyStepConstructor_returnsVerifyStep() {
        final var scenarioMock           = mock(Scenario.class);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);

        when(defaultVerifyStepConstructorMock.constructVerifyStep(scenarioMock, expectOrVerifyStepMock))
                .thenReturn(verifyStepMock);

        final var result = defaultStepConstructor.constructVerifyStep(scenarioMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }
}
