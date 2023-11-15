package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PolicyFolder;
import io.sapl.test.grammar.sAPLTest.PolicySet;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TestProvider {

    private final StepConstructor stepConstructor;

    public List<TestContainer> buildTests(final SAPLTest saplTest) {
        if (saplTest == null) {
            throw new SaplTestException("provided SAPLTest is null");
        }

        final var testSuites = saplTest.getElements();

        if (testSuites == null || testSuites.isEmpty()) {
            throw new SaplTestException("provided SAPLTest does not contain a TestSuite");
        }

        return testSuites.stream().map(testSuite -> {
            final var testCases = testSuite.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                throw new SaplTestException("provided TestSuite does not contain a Test");
            }

            final var name = getDynamicContainerName(testSuite);

            return TestContainer.from(name, testCases.stream().map(testCase -> TestCase.from(stepConstructor, testSuite, testCase)).toList());
        }).toList();
    }

    private String getDynamicContainerName(final TestSuite testSuite) {
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            return unitTestSuite.getId();
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            final var policyResolverConfig = integrationTestSuite.getConfig();
            if (policyResolverConfig instanceof PolicyFolder policyFolderConfig) {
                return policyFolderConfig.getPolicyFolder();
            } else if (policyResolverConfig instanceof PolicySet policySet) {
                return String.join(",", policySet.getPolicies());
            } else {
                throw new SaplTestException("Unknown type of PolicyResolverConfig");
            }
        } else {
            throw new SaplTestException("Unknown type of TestSuite");
        }
    }
}
