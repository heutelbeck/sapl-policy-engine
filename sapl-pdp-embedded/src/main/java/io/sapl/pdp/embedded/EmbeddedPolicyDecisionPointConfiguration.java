package io.sapl.pdp.embedded;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.PolicyCombiningAlgorithm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddedPolicyDecisionPointConfiguration implements Serializable {
	private static final long serialVersionUID = 1L;
	PolicyCombiningAlgorithm algorithm = PolicyCombiningAlgorithm.DENY_UNLESS_PERMIT;
	HashMap<String, JsonNode> variables = new HashMap<>();
	HashSet<String> attributeFinders = new HashSet<>();
	HashSet<String> libraries = new HashSet<>();
	PrpImplementation prpImplementation;
}
