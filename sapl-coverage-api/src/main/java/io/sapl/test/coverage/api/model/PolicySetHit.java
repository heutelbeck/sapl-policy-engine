package io.sapl.test.coverage.api.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
/**
 * Containing all necessary information of a {@link io.sapl.grammar.sapl.PolicySet} hit
 */
public class PolicySetHit {
	/**
	 * PolicySetId of hit {@link io.sapl.grammar.sapl.PolicySet}
	 */
	private String policySetId;
	
	@Override
	public String toString() {
		return policySetId;
	}
	
	public static PolicySetHit fromString(String policySetToStringResult) {
		return new PolicySetHit(policySetToStringResult);
	}
}
