package io.sapl.api.pdp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionPointConfiguration {

	private PolicyDocumentCombiningAlgorithm algorithm = PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;

	private Map<String, JsonNode> variables = new HashMap<>();

}
