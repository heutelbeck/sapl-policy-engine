package io.sapl.test.dsl.factories;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interpreter.*;
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
    public static TestProvider create(StepConstructor stepConstructor) {
        if (stepConstructor == null) {
            stepConstructor = getDefaultStepConstructor();
        }

        return new TestProvider(stepConstructor);
    }

    private static DefaultTestFixtureBuilder getTestFixtureBuilder(final ValInterpreter valInterpreter) {
        final var pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();

        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, pdpCombiningAlgorithmInterpreter);

        final var functionLibraryInterpreter = new FunctionLibraryInterpreter();
        final var reflectionHelper = new ReflectionHelper();

        return new DefaultTestFixtureBuilder(testSuiteInterpreter, functionLibraryInterpreter, reflectionHelper);
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
