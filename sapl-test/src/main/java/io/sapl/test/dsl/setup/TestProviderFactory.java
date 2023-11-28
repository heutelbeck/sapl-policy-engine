package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.interpreter.DefaultStepConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class TestProviderFactory {

    public static TestProvider create(final StepConstructor stepConstructor) {
        if (stepConstructor == null) {
            throw new SaplTestException("Provided stepConstructor is null");
        }

        return TestProvider.of(stepConstructor);
    }

    public static TestProvider create(final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var stepConstructor = DefaultStepConstructor.of(customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);

        return TestProvider.of(stepConstructor);
    }
}
