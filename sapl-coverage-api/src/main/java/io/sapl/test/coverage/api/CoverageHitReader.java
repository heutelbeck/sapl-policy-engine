package io.sapl.test.coverage.api;

import java.util.List;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public interface CoverageHitReader {
	
	/**
	 * Internal method used by SAPL Coverage Reading to read all hits of {@link io.sapl.grammar.sapl.PolicySet}'s
	 * @return List of {@link PolicySetHit}
	 */
	List<PolicySetHit> readPolicySetHits();
	
	/**
	 * Internal method used by SAPL Coverage Reading to read all hits of {@link io.sapl.grammar.sapl.Policy}'s
	 * @return List of {@link PolicySetHit}
	 */
	List<PolicyHit> readPolicyHits();
	
	/**
	 * Internal method used by SAPL Coverage Reading to read all hits of {@link io.sapl.grammar.sapl.Condition}'s
	 * @return List of {@link PolicySetHit}
	 */
	List<PolicyConditionHit> readPolicyConditionHits();
	
	/**
	 * Deletes all files used for coverage recording
	 */
	void cleanCoverageHitFiles();
}
