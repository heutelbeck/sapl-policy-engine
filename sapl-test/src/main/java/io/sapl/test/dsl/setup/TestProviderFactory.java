package io.sapl.test.dsl.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.dsl.ReflectionHelper;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interpreter.*;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import io.sapl.test.dsl.interpreter.matcher.AuthorizationDecisionMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.JsonNodeMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.MultipleAmountInterpreter;
import io.sapl.test.dsl.interpreter.matcher.StringMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.ValMatcherInterpreter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestProviderFactory {
    public static TestProvider create(StepConstructor stepConstructor) {
        if (stepConstructor == null) {
            stepConstructor = getDefaultStepConstructor();
        }

        return new TestProvider(stepConstructor);
    }

    private static TestFixtureBuilder getTestFixtureBuilder(final ValInterpreter valInterpreter) {
        final var pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();

        final var saplUnitTestFixtureConstructorWrapper = new SaplUnitTestFixtureConstructorWrapper();
        final var saplIntegrationTestFixtureConstructorWrapper = new SaplIntegrationTestFixtureConstructorWrapper();
        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, pdpCombiningAlgorithmInterpreter, saplUnitTestFixtureConstructorWrapper, saplIntegrationTestFixtureConstructorWrapper);

        final var functionLibraryInterpreter = new FunctionLibraryInterpreter();
        final var reflectionHelper = new ReflectionHelper();

        return new TestFixtureBuilder(testSuiteInterpreter, functionLibraryInterpreter, reflectionHelper);
    }

    private static StepConstructor getDefaultStepConstructor() {
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


        final var testFixtureConstructor = getTestFixtureBuilder(valInterpreter);
        final var whenStepBuilder = new DefaultWhenStepConstructor(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new DefaultExpectStepConstructor(authorizationSubscriptionInterpreter);
        final var verifyStepBuilder = new DefaultVerifyStepConstructor(expectInterpreter);

        return new DefaultStepConstructor(expectStepBuilder, testFixtureConstructor, verifyStepBuilder, whenStepBuilder);
    }
}
