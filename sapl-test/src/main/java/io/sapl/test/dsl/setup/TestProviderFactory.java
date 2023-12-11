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

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.interpreter.DefaultStepConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class TestProviderFactory {

    public static TestProvider create(final StepConstructor stepConstructor) {
        if (stepConstructor == null) {
            throw new SaplTestException("Provided stepConstructor is null");
        }

        return TestProvider.of(stepConstructor);
    }

    public static TestProvider create(final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var stepConstructor = DefaultStepConstructor.of(customUnitTestPolicyResolver,
                customIntegrationTestPolicyResolver);

        return TestProvider.of(stepConstructor);
    }
}
