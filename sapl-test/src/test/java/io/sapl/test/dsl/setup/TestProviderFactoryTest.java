/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.interpreter.DefaultStepConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TestProviderFactoryTest {

    private final MockedStatic<TestProvider> testProviderMockedStatic = mockStatic(TestProvider.class);

    @AfterEach
    void tearDown() {
        testProviderMockedStatic.close();
    }

    @Test
    void create_withNullStepConstructor_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestProviderFactory.create(null));

        assertEquals("Provided stepConstructor is null", exception.getMessage());
    }

    @Test
    void create_usesGivenStepConstructor_returnsTestProvider() {
        final var stepConstructorMock = mock(StepConstructor.class);

        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(stepConstructorMock)).thenReturn(testProviderMock);

        final var result = TestProviderFactory.create(stepConstructorMock);

        assertEquals(testProviderMock, result);
    }

    @Test
    void create_withUnitTestPolicyResolverAndIntegrationTestPolicyResolver_returnsTestProvider() {
        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(any(DefaultStepConstructor.class)))
                .thenReturn(testProviderMock);

        final var unitTestPolicyResolver        = mock(UnitTestPolicyResolver.class);
        final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);

        final var result = TestProviderFactory.create(unitTestPolicyResolver, integrationTestPolicyResolver);

        assertEquals(testProviderMock, result);
    }
}
