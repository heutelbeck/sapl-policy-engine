package io.sapl.api.pdp;

import java.io.Serializable;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionPointConfiguration implements Serializable {

	private static final long serialVersionUID = 1L;

	private PolicyDocumentCombiningAlgorithm algorithm = PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;

	private HashMap<String, JsonNode> variables = new HashMap<>();

}
