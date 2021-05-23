package io.sapl.mavenplugin.test.coverage.model;

import java.util.Collection;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CoverageTargets {

	private Collection<PolicySetHit> policySets;

	private Collection<PolicyHit> policys;

	private Collection<PolicyConditionHit> policyConditions;
	
	public boolean isPolicySetHit(PolicySetHit possibleHit) {
		return this.policySets.contains(possibleHit);
	}
	
	public boolean isPolicyHit(PolicyHit possibleHit) {
		return this.policys.contains(possibleHit);
	}
		
	public boolean isPolicyConditionHit(PolicyConditionHit possibleHit) {
		return this.policyConditions.contains(possibleHit);
	}
}
