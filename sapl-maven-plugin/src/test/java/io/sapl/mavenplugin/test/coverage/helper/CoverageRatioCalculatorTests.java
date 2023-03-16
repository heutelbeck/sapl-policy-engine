/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageRatioCalculatorTests {

	@Test
	void test_normalPath() {
		var calculator = new CoverageRatioCalculator();
		var targets = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
		var hits = List.of(new PolicySetHit("set1"));

		var ratio = calculator.calculateRatio(targets, hits);

		assertEquals(50.0f, ratio);
	}

	@Test
	void test_listOfHitsContainsElementsNotInAvailableTargets() {
		var calculator = new CoverageRatioCalculator();
		var targets = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
		var hits = List.of(new PolicySetHit("set1"), new PolicySetHit("set999"));

		var ratio = calculator.calculateRatio(targets, hits);

		assertEquals(50.0f, ratio);
	}

	@Test
	void test_EmptyTargetColletion() {
		var calculator = new CoverageRatioCalculator();
		List<PolicySetHit> targets = List.of();
		var hits = List.of(new PolicySetHit("set1"), new PolicySetHit("set999"));

		var ratio = calculator.calculateRatio(targets, hits);

		assertEquals(0f, ratio);
	}

	@Test
	void test_zeroHits() {
		var calculator = new CoverageRatioCalculator();
		var targets = List.of(new PolicySetHit("set1"), new PolicySetHit("set2"));
		List<PolicySetHit> hits = List.of();

		var ratio = calculator.calculateRatio(targets, hits);

		assertEquals(0f, ratio);

	}

}
