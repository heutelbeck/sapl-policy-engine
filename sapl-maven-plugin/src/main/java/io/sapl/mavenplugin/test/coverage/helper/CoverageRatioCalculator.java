package io.sapl.mavenplugin.test.coverage.helper;

import java.util.List;

import io.sapl.mavenplugin.test.coverage.model.CoverageHitSummary;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class CoverageRatioCalculator {
	
	public static float calculatePolicySetHitRatio(List<PolicySetHit> availableCoverageTargets, List<PolicySetHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicySetHit hit : hitTargets) {
			if(CoverageHitSummary.isPolicySetHit(availableCoverageTargets, hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}
	
	public static float calculatePolicyHitRatio(List<PolicyHit> availableCoverageTargets, List<PolicyHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicyHit hit : hitTargets) {
			if(CoverageHitSummary.isPolicyHit(availableCoverageTargets, hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}
	
	public static float calculatePolicyConditionHitRatio(List<PolicyConditionHit> availableCoverageTargets, List<PolicyConditionHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicyConditionHit hit : hitTargets) {
			if(CoverageHitSummary.isPolicyConditionHit(availableCoverageTargets, hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}

}
