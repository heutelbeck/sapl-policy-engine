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
package io.sapl.mavenplugin.test.coverage.helper;

import io.sapl.test.coverage.api.model.PolicySetHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageRatioCalculatorTests {

    @Test
    void test_normalPath() {
        final var calculator = new CoverageRatioCalculator();
        final var targets    = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
        final var hits       = List.of(new PolicySetHit("set1"));

        final var ratio = calculator.calculateRatio(targets, hits);

        assertEquals(50.0f, ratio);
    }

    @Test
    void test_listOfHitsContainsElementsNotInAvailableTargets() {
        final var calculator = new CoverageRatioCalculator();
        final var targets    = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
        final var hits       = List.of(new PolicySetHit("set1"), new PolicySetHit("set999"));

        final var ratio = calculator.calculateRatio(targets, hits);

        assertEquals(50.0f, ratio);
    }

    @Test
    void test_EmptyTargetCollection() {
        final var          calculator = new CoverageRatioCalculator();
        List<PolicySetHit> targets    = List.of();
        final var          hits       = List.of(new PolicySetHit("set1"), new PolicySetHit("set999"));

        final var ratio = calculator.calculateRatio(targets, hits);

        assertEquals(0f, ratio);
    }

    @Test
    void test_zeroHits() {
        final var          calculator = new CoverageRatioCalculator();
        final var          targets    = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
        List<PolicySetHit> hits       = List.of();

        final var ratio = calculator.calculateRatio(targets, hits);

        assertEquals(0f, ratio);

    }

}
