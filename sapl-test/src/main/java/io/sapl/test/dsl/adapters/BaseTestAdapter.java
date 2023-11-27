package io.sapl.test.dsl.adapters;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.factories.SaplTestInterpreterFactory;
import io.sapl.test.dsl.factories.TestProviderFactory;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.utils.DocumentHelper;

public abstract class BaseTestAdapter<T> {

    private final SaplTestInterpreter saplTestInterpreter;
    private final TestProvider testProvider;

    protected BaseTestAdapter(final StepConstructor stepConstructor, final SaplTestInterpreter saplTestInterpreter) {
        this.testProvider = TestProviderFactory.create(stepConstructor);
        this.saplTestInterpreter = saplTestInterpreter;
    }

    protected BaseTestAdapter(final SaplTestInterpreter saplTestInterpreter, final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        this.testProvider = TestProviderFactory.create(customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
        this.saplTestInterpreter = saplTestInterpreter;
    }

    protected BaseTestAdapter(final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        this(SaplTestInterpreterFactory.create(), customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
    }

    protected BaseTestAdapter() {
        this(SaplTestInterpreterFactory.create(), null, null);
    }


    protected T createTest(final String filename) {
        if (filename == null) {
            throw new SaplTestException("provided filename is null");
        }

        final var input = DocumentHelper.findFileOnClasspath(filename);

        if (input == null) {
            throw new SaplTestException("file does not exist");
        }

        return createTestContainerAndConvertToTargetRepresentation(filename, input);
    }

    protected T createTest(final String identifier, final String testDefinition) {
        if (identifier == null || testDefinition == null) {
            throw new SaplTestException("identifier or input is null");
        }

        return createTestContainerAndConvertToTargetRepresentation(identifier, testDefinition);
    }

    private T createTestContainerAndConvertToTargetRepresentation(final String identifier, final String testDefinition) {
        final var saplTest = saplTestInterpreter.loadAsResource(testDefinition);

        final var testContainer = TestContainer.from(identifier, testProvider.buildTests(saplTest));

        return convertTestContainerToTargetRepresentation(testContainer);
    }

    protected abstract T convertTestContainerToTargetRepresentation(final TestContainer testContainer);
}
