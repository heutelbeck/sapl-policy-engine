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

import java.util.Collection;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import lombok.experimental.UtilityClass;

@UtilityClass
class SaplIntegrationTestFixtureFactory {
    public static SaplIntegrationTestFixture create(final String folderPath) {
        return new SaplIntegrationTestFixture(folderPath);
    }

    public static SaplIntegrationTestFixture create(final String pdpConfigPath, final Collection<String> policyPaths) {
        return new SaplIntegrationTestFixture(pdpConfigPath, policyPaths);
    }

    public static SaplIntegrationTestFixture createFromInputStrings(final Collection<String> documentInputStrings,
            final String pdpConfigInputString) {
        return new SaplIntegrationTestFixture(documentInputStrings, pdpConfigInputString);
    }
}
