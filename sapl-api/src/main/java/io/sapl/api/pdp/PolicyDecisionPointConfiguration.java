package io.sapl.api.pdp;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionPointConfiguration implements Serializable {
	private static final long serialVersionUID = 1L;
	PolicyCombiningAlgorithm algorithm = PolicyCombiningAlgorithm.DENY_UNLESS_PERMIT;
	HashMap<String, String> variables = new HashMap<>();
	HashSet<String> attributeFinders = new HashSet<>();
	HashSet<String> libraries = new HashSet<>();

	public PolicyDecisionPointConfiguration(PolicyDecisionPointConfiguration config) {
		algorithm = config.getAlgorithm();
		variables = new HashMap<>(config.getVariables());
		attributeFinders = new HashSet<>(config.getAttributeFinders());
		libraries = new HashSet<>(config.getLibraries());
	}
}
