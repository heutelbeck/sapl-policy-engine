package io.sapl.interpreter;

import java.util.List;

public class PolicySetDecision extends PolicyDecision {
	String                        combiningAlgorithm;
	List<PolicyDecision> policyDecisions;
}
