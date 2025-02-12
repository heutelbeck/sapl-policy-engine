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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AuthorizationSubscription;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;

@ExtendWith(MockitoExtension.class)
class DefaultExpectStepConstructorTests {
    @Mock
    protected AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreterMock;
    @InjectMocks
    protected DefaultExpectStepConstructor         defaultExpectStepConstructor;
    @Mock
    protected Scenario                             scenarioMock;
    @Mock
    protected WhenStep                             whenStepMock;

    protected final MockedStatic<io.sapl.api.pdp.AuthorizationSubscription> authorizationSubscriptionMockedStatic = mockStatic(
            io.sapl.api.pdp.AuthorizationSubscription.class);

    @AfterEach
    void tearDown() {
        authorizationSubscriptionMockedStatic.close();
    }

    @Test
    void constructExpectStep_handlesNullScenario_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultExpectStepConstructor.constructExpectStep(null, whenStepMock));

        assertEquals("Scenario or whenStep is null", exception.getMessage());
    }

    @Test
    void constructExpectStep_handlesNullWhenStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultExpectStepConstructor.constructExpectStep(scenarioMock, null));

        assertEquals("Scenario or whenStep is null", exception.getMessage());
    }

    @Test
    void constructExpectStep_handlesNullScenarioAndNullWhenStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultExpectStepConstructor.constructExpectStep(null, null));

        assertEquals("Scenario or whenStep is null", exception.getMessage());
    }

    @Test
    void constructExpectStep_handlesScenarioAndNullWhenStep_throwsSaplTestException() {
        when(scenarioMock.getWhenStep()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultExpectStepConstructor.constructExpectStep(scenarioMock, whenStepMock));

        assertEquals("Scenario does not contain a whenStep", exception.getMessage());
    }

    @Test
    void constructExpectStep_handlesNullAuthorizationSubscription_throwsSaplTestException() {
        final var saplTestWhenStepMock = mock(io.sapl.test.grammar.sapltest.WhenStep.class);
        when(scenarioMock.getWhenStep()).thenReturn(saplTestWhenStepMock);

        when(saplTestWhenStepMock.getAuthorizationSubscription()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultExpectStepConstructor.constructExpectStep(scenarioMock, whenStepMock));

        assertEquals("AuthorizationSubscription is null", exception.getMessage());
    }

    @Test
    void constructExpectStep_returnsCorrectExpectStep() {
        final var saplTestWhenStepMock = mock(io.sapl.test.grammar.sapltest.WhenStep.class);
        when(scenarioMock.getWhenStep()).thenReturn(saplTestWhenStepMock);

        final var authorizationSubscriptionMock = mock(AuthorizationSubscription.class);
        when(saplTestWhenStepMock.getAuthorizationSubscription()).thenReturn(authorizationSubscriptionMock);

        final var saplAuthorizationSubscriptionMock = mock(io.sapl.api.pdp.AuthorizationSubscription.class);
        when(authorizationSubscriptionInterpreterMock.constructAuthorizationSubscription(authorizationSubscriptionMock))
                .thenReturn(saplAuthorizationSubscriptionMock);

        final var aWhenStepMock  = mock(WhenStep.class);
        final var expectStepMock = mock(ExpectStep.class);
        when(aWhenStepMock.when(saplAuthorizationSubscriptionMock)).thenReturn(expectStepMock);

        final var result = defaultExpectStepConstructor.constructExpectStep(scenarioMock, aWhenStepMock);
        assertEquals(expectStepMock, result);
    }
}
