package io.sapl.mavenplugin.test.coverage.model;

import java.util.List;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CoverageHitSummary {

	List<PolicySetHit> policySets;

	List<PolicyHit> policys;

	List<PolicyConditionHit> policyConditions;
	
	public boolean isPolicySetHit(String policySetName) {
		return isPolicySetHit(this.policySets, new PolicySetHit(policySetName));
	}
	
	public static boolean isPolicySetHit(List<PolicySetHit> policySets, PolicySetHit possibleHit) {
		return policySets.stream().map(set -> set.equals(possibleHit)).anyMatch(result -> result.equals(Boolean.TRUE));
	}
	
	public boolean isPolicyHit(String policySetName, String policyName) {
		return isPolicyHit(this.policys, new PolicyHit(policySetName, policyName));
	}
	
	public static boolean isPolicyHit(List<PolicyHit> policys, PolicyHit possibleHit) {
		return policys.stream().map(policy ->  policy.equals(possibleHit)).anyMatch(result -> result.equals(Boolean.TRUE));
	}
	
	public boolean isPolicyConditionHit(String policySetName, String policyName, int statementId, boolean conditionResult) {
		return isPolicyConditionHit(this.policyConditions, new PolicyConditionHit(policySetName, policyName, statementId, conditionResult));
	}
	
	public static boolean isPolicyConditionHit(List<PolicyConditionHit> policyConditions, PolicyConditionHit possibleHit) {
		return policyConditions.stream().map(condition -> condition.equals(possibleHit)).anyMatch(result -> result.equals(Boolean.TRUE));
	}
}
