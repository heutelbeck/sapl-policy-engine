package io.sapl.mavenplugin.test.coverage.helper;

import java.util.Collection;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class CoverageRatioCalculator {
	
	public <T> float calculateRatio(Collection<T> availableCoverageTargets, Collection<T> hitTargets) {
		float targets = availableCoverageTargets.size();
		int hits = 0;
		
		for(T hit : hitTargets) {
			if(availableCoverageTargets.contains(hit)) {
				hits++;
			}
		}
		
		if(hitTargets.isEmpty() || hits == 0) {
			return 0;
		}
		
		return (hits / targets) * 100;
	}
}
