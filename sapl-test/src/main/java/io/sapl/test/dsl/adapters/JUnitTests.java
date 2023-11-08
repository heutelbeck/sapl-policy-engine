package io.sapl.test.dsl.adapters;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.setup.Test;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestDiscoveryHelper;
import io.sapl.test.dsl.setup.TestNode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class JUnitTests extends BaseTestAdapter<DynamicContainer> {

    @TestFactory
    @DisplayName("DSLTests")
    public List<DynamicContainer> getTests() {
        final var paths = TestDiscoveryHelper.discoverTests();
        if (paths == null) {
            return Collections.emptyList();
        }
        return paths.stream().map(this::createTest).toList();
    }

    private List<DynamicNode> getDynamicContainersFromTestNode(List<? extends TestNode> testNodes) {
        if (testNodes == null) {
            return Collections.emptyList();
        }

        return testNodes.stream().map(testNode -> {
            if (testNode instanceof Test testCase) {
                return DynamicTest.dynamicTest(testCase.getIdentifier(), testCase::run);
            } else if (testNode instanceof TestContainer testContainer) {
                return dynamicContainer(testContainer.getIdentifier(), getDynamicContainersFromTestNode(testContainer.getTestNodes()));
            }
            throw new SaplTestException("Unknown type of TestNode");
        }).toList();
    }

    @Override
    protected DynamicContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
        return dynamicContainer(testContainer.getIdentifier(), getDynamicContainersFromTestNode(testContainer.getTestNodes()));
    }
}
