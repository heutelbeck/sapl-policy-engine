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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.sapl.test.integration.SaplIntegrationTestFixture;

class SaplIntegrationTestFixtureFactoryTests {

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithFolderPath_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.create("path");

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithPDPConfigAndPolicyPaths_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.create("pdpConfigPath", Collections.emptyList());

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }

    @Test
    void createFromInputStrings_constructsInstanceOfSaplIntegrationTestFixtureWithInputStringsAndPdpConfigInputString_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.createFromInputStrings(Collections.emptyList(), "");

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }
}
