package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PdpConfigurationHandler {

    private final ValueInterpreter              valueInterpreter;
    private final CombiningAlgorithmInterpreter combiningAlgorithmInterpreter;

    public SaplTestFixture applyPdpConfigurationToFixture(SaplIntegrationTestFixture integrationTestFixture,
            final PdpVariables pdpVariables, final PdpCombiningAlgorithm pdpCombiningAlgorithm) {
        if (pdpVariables != null
                && pdpVariables.getPdpVariables() instanceof io.sapl.test.grammar.sapltest.Object object) {
            final var pdpEnvironmentVariables = valueInterpreter.destructureObject(object);
            integrationTestFixture = integrationTestFixture
                    .withPDPVariables(packageJsonNodesInVal(pdpEnvironmentVariables));
        }

        if (pdpCombiningAlgorithm != null && pdpCombiningAlgorithm.isCombiningAlgorithmDefined()) {
            final var pdpPolicyCombiningAlgorithm = combiningAlgorithmInterpreter
                    .interpretPdpCombiningAlgorithm(pdpCombiningAlgorithm.getCombiningAlgorithm());
            integrationTestFixture = integrationTestFixture
                    .withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
        }

        return integrationTestFixture;
    }

    private Map<String, Val> packageJsonNodesInVal(Map<String, JsonNode> originalMap) {
        var newMap = new HashMap<String, Val>();
        originalMap.forEach((key, json) -> newMap.put(key, Val.of(json)));
        return newMap;
    }
}
