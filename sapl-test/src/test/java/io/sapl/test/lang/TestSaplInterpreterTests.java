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
package io.sapl.test.lang;

import io.sapl.test.coverage.api.CoverageHitRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestSaplInterpreterTests {

    private static final String POLICY_ID = "test";

    @Test
    void testTestModeImplAreCreatedIfSystemPropertyIsNotSet() {
        final var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
        final var policyDocument = "policy \"" + POLICY_ID + "\" permit";
        final var document       = interpreter.parse(policyDocument);

        assertThat(document.getPolicyElement()).isInstanceOf(PolicyImplCustomCoverage.class);
    }

    @Test
    void testTestModeImplAreCreatedIfSystemPropertyIsTrue() {
        System.setProperty("io.sapl.test.coverage.collect", "true");
        final var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
        final var policyDocument = "policy \"" + POLICY_ID + "\" permit";
        final var document       = interpreter.parse(policyDocument);

        assertThat(document.getPolicyElement()).isInstanceOf(PolicyImplCustomCoverage.class);
        System.clearProperty("io.sapl.test.coverage.collect");
    }

    @Test
    void testTestModeImplAreCreatedIfSystemPropertyIsFalse() {
        System.setProperty("io.sapl.test.coverage.collect", "false");
        final var interpreter    = new TestSaplInterpreter(Mockito.mock(CoverageHitRecorder.class));
        final var policyDocument = "policy \"" + POLICY_ID + "\" permit";
        final var document       = interpreter.parse(policyDocument);

        assertThat(document.getPolicyElement()).isNotInstanceOf(PolicyImplCustomCoverage.class);
        System.clearProperty("io.sapl.test.coverage.collect");
    }

}
