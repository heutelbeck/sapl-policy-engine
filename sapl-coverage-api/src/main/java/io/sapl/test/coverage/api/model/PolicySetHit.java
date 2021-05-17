package io.sapl.test.coverage.api.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
/**
 * Containing all neccessary information of a {@link io.sapl.grammar.sapl.PolicySet} hit
 *
 */
public class PolicySetHit {
	/**
	 * PolicySetId of hit {@link io.sapl.grammar.sapl.PolicySet}
	 */
	private String policySetId;
	
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(policySetId);
		return stringBuilder.toString();
	}
	
	public static PolicySetHit fromString(String policySetToStringResult) {
		return new PolicySetHit(policySetToStringResult);
	}
}
