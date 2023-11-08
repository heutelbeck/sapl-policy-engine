package io.sapl.test.dsl.adapters;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.setup.SaplTestInterpreterFactory;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.dsl.setup.TestProviderFactory;
import io.sapl.test.utils.DocumentHelper;

public abstract class BaseTestAdapter<T> {

    private final SaplTestInterpreter saplTestInterpreter;
    private final TestProvider testProvider;

    protected BaseTestAdapter(final StepConstructor stepConstructor, final SaplTestInterpreter saplTestInterpreter) {
        this.testProvider = TestProviderFactory.create(stepConstructor);
        this.saplTestInterpreter = saplTestInterpreter;
    }

    protected BaseTestAdapter() {
        this(null, SaplTestInterpreterFactory.create());
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

    protected T createTest(final String identifier, final String input) {
        if (identifier == null || input == null) {
            throw new SaplTestException("identifier or input is null");
        }

        return createTestContainerAndConvertToTargetRepresentation(identifier, input);
    }

    private T createTestContainerAndConvertToTargetRepresentation(final String identifier, final String input) {
        final var saplTest = saplTestInterpreter.loadAsResource(input);

        return convertTestContainerToTargetRepresentation(TestContainer.from(identifier, testProvider.buildTests(saplTest)));
    }

    protected abstract T convertTestContainerToTargetRepresentation(final TestContainer testContainer);
}
