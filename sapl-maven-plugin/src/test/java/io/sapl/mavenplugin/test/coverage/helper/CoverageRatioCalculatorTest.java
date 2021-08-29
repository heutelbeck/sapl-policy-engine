package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageRatioCalculatorTest {

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
