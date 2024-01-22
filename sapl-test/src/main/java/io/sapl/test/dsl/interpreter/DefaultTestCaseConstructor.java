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

import io.sapl.test.SaplTestFixture;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultTestCaseConstructor {

    private final ValueInterpreter valueInterpreter;

    GivenOrWhenStep constructTestCase(final SaplTestFixture saplTestFixture,
            final io.sapl.test.grammar.sapltest.Object environment, final boolean needsMocks) {

        if (environment != null) {
            final var environmentVariables = valueInterpreter.destructureObject(environment);

            if (environmentVariables != null) {
                environmentVariables.forEach(saplTestFixture::registerVariable);
            }
        }

        return (GivenOrWhenStep) (needsMocks ? saplTestFixture.constructTestCaseWithMocks()
                : saplTestFixture.constructTestCase());
    }
}
