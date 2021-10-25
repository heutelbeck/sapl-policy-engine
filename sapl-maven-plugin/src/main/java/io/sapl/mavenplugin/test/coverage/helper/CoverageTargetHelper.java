package io.sapl.mavenplugin.test.coverage.helper;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Statement;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class CoverageTargetHelper {

	public CoverageTargets getCoverageTargets(Collection<SaplDocument> documents) {
		List<PolicySetHit> availablePolicySetHitTargets = new LinkedList<>();
		List<PolicyHit> availablePolicyHitTargets = new LinkedList<>();
		List<PolicyConditionHit> availablePolicyConditionHitTargets = new LinkedList<>();

		for (SaplDocument saplDoc : documents) {
			PolicyElement element = saplDoc.getSaplDocument().getPolicyElement();

			if (element.eClass().equals(SaplPackage.Literals.POLICY_SET)) {
				addPolicySetToResult((PolicySet) element, availablePolicySetHitTargets, availablePolicyHitTargets,
						availablePolicyConditionHitTargets);
			} else if (element.eClass().equals(SaplPackage.Literals.POLICY)) {
				addPolicyToResult((Policy) element, "", availablePolicyHitTargets, availablePolicyConditionHitTargets);
			} else {
				throw new SaplTestException("Error: Unknown Subtype of " + PolicyElement.class);
			}
		}

		return new CoverageTargets(List.copyOf(availablePolicySetHitTargets), List.copyOf(availablePolicyHitTargets),
				List.copyOf(availablePolicyConditionHitTargets));
	}

	

	private void addPolicySetToResult(PolicySet policySet, Collection<PolicySetHit> availablePolicySetHitTargets,
			Collection<PolicyHit> availablePolicyHitTargets,
			Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

		availablePolicySetHitTargets.add(new PolicySetHit(policySet.getSaplName()));

		for (Policy policy : policySet.getPolicies()) {
			addPolicyToResult(policy, policySet.getSaplName(), availablePolicyHitTargets,
					availablePolicyConditionHitTargets);
		}
	}

	private void addPolicyToResult(Policy policy, String policySetId, Collection<PolicyHit> availablePolicyHitTargets,
			Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

		availablePolicyHitTargets.add(new PolicyHit(policySetId, policy.getSaplName()));

		if (policy.getBody() == null) {
			return;
		}

		for (int i = 0; i < policy.getBody().getStatements().size(); i++) {
			addPolicyConditionToResult(policy.getBody().getStatements().get(i), i, policySetId, policy.getSaplName(),
					availablePolicyConditionHitTargets);
		}
	}

	private void addPolicyConditionToResult(Statement statement, int position, String policySetId, String policyId,
			Collection<PolicyConditionHit> availablePolicyConditionHitTargets) {

		if (statement instanceof Condition) {
			availablePolicyConditionHitTargets.add(new PolicyConditionHit(policySetId, policyId, position, true));
			availablePolicyConditionHitTargets.add(new PolicyConditionHit(policySetId, policyId, position, false));
		}
	}


}
