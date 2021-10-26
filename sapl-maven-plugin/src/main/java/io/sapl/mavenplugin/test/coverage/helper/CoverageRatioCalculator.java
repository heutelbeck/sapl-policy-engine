/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
