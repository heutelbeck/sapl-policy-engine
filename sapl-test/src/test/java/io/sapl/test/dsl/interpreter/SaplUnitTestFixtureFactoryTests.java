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

import org.junit.jupiter.api.Test;

import io.sapl.test.unit.SaplUnitTestFixture;

class SaplUnitTestFixtureFactoryTests {

    @Test
    void create_constructsInstanceOfSaplUnitTestFixtureWithSaplDocumentName_returnsSaplUnitTestFixture() {
        final var result = SaplUnitTestFixtureFactory.create("documentName");

        assertInstanceOf(SaplUnitTestFixture.class, result);
    }

    @Test
    void createFromInputString_constructsInstanceOfSaplUnitTestFixtureWithInputString_returnsSaplUnitTestFixture() {
        final var result = SaplUnitTestFixtureFactory.createFromInputString("documentName");

        assertInstanceOf(SaplUnitTestFixture.class, result);
    }
}
