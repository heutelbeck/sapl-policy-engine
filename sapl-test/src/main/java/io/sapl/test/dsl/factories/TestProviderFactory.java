package io.sapl.test.dsl.factories;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.interpreter.AttributeInterpreter;
import io.sapl.test.dsl.interpreter.AuthorizationDecisionInterpreter;
import io.sapl.test.dsl.interpreter.AuthorizationSubscriptionInterpreter;
import io.sapl.test.dsl.interpreter.DefaultExpectStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultTestFixtureConstructor;
import io.sapl.test.dsl.interpreter.DefaultVerifyStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultWhenStepConstructor;
import io.sapl.test.dsl.interpreter.DurationInterpreter;
import io.sapl.test.dsl.interpreter.ExpectInterpreter;
import io.sapl.test.dsl.interpreter.FunctionInterpreter;
import io.sapl.test.dsl.interpreter.FunctionLibraryInterpreter;
import io.sapl.test.dsl.interpreter.PDPCombiningAlgorithmInterpreter;
import io.sapl.test.dsl.interpreter.TestSuiteInterpreter;
import io.sapl.test.dsl.interpreter.ValInterpreter;
import io.sapl.test.dsl.interpreter.matcher.AuthorizationDecisionMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.JsonNodeMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.MultipleAmountInterpreter;
import io.sapl.test.dsl.interpreter.matcher.StringMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.ValMatcherInterpreter;
import io.sapl.test.dsl.setup.DefaultStepConstructor;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.dsl.utils.ReflectionHelper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestProviderFactory {

    public static TestProvider create(final StepConstructor stepConstructor) {
        if (stepConstructor == null) {
            throw new SaplTestException("Provided stepConstructor is null");
        }

        return TestProvider.of(stepConstructor);
    }

    public static TestProvider create(final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var stepConstructor = getDefaultStepConstructor(customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);

        return TestProvider.of(stepConstructor);
    }

    private static DefaultTestFixtureConstructor getTestFixtureConstructor(final ValInterpreter valInterpreter, final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();

        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, pdpCombiningAlgorithmInterpreter, customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);

        final var functionLibraryInterpreter = new FunctionLibraryInterpreter();
        final var reflectionHelper = new ReflectionHelper();

        return new DefaultTestFixtureConstructor(testSuiteInterpreter, functionLibraryInterpreter, reflectionHelper);
    }

    private static StepConstructor getDefaultStepConstructor(final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        final var objectMapper = new ObjectMapper();
        final var valInterpreter = new ValInterpreter(objectMapper);

        final var stringMatcherInterpreter = new StringMatcherInterpreter();
        final var jsonNodeMatcherInterpreter = new JsonNodeMatcherInterpreter(stringMatcherInterpreter);

        final var matcherInterpreter = new ValMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter, stringMatcherInterpreter);

        final var durationInterpreter = new DurationInterpreter();
        final var attributeInterpreter = new AttributeInterpreter(valInterpreter, matcherInterpreter, durationInterpreter);
        final var multipleAmountInterpreter = new MultipleAmountInterpreter();
        final var functionInterpreter = new FunctionInterpreter(valInterpreter, matcherInterpreter, multipleAmountInterpreter);
        final var authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(valInterpreter, objectMapper);
        final var authorizationSubscriptionInterpreter = new AuthorizationSubscriptionInterpreter(valInterpreter);
        final var authorizationDecisionMatcherInterpreter = new AuthorizationDecisionMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter);
        final var expectInterpreter = new ExpectInterpreter(valInterpreter, authorizationDecisionInterpreter, authorizationDecisionMatcherInterpreter, durationInterpreter, multipleAmountInterpreter);

        final var defaultTestFixtureConstructor = getTestFixtureConstructor(valInterpreter, customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
        final var whenStepBuilder = new DefaultWhenStepConstructor(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new DefaultExpectStepConstructor(authorizationSubscriptionInterpreter);
        final var verifyStepBuilder = new DefaultVerifyStepConstructor(expectInterpreter);

        return new DefaultStepConstructor(defaultTestFixtureConstructor, whenStepBuilder, expectStepBuilder, verifyStepBuilder);
    }
}
