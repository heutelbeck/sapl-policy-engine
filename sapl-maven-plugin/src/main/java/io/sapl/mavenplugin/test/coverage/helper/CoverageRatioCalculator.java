package io.sapl.mavenplugin.test.coverage.helper;

import java.util.Collection;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class CoverageRatioCalculator {
	
	public static float calculatePolicySetHitRatio(Collection<PolicySetHit> availableCoverageTargets, Collection<PolicySetHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicySetHit hit : hitTargets) {
			if(availableCoverageTargets.contains(hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}
	
	public static float calculatePolicyHitRatio(Collection<PolicyHit> availableCoverageTargets, Collection<PolicyHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicyHit hit : hitTargets) {
			if(availableCoverageTargets.contains(hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}
	
	public static float calculatePolicyConditionHitRatio(Collection<PolicyConditionHit> availableCoverageTargets, Collection<PolicyConditionHit> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(PolicyConditionHit hit : hitTargets) {
			if(availableCoverageTargets.contains(hit)) {
				hits++;
			}
		}
		
		if(hitTargets.size() == 0 || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}

}
