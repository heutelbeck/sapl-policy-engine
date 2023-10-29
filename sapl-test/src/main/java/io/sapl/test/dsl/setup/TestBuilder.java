package io.sapl.test.dsl.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ReflectionHelper;
import io.sapl.test.dsl.interfaces.JUnitDynamicTestBuilder;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interpreter.*;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import io.sapl.test.dsl.interpreter.matcher.AuthorizationDecisionMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.JsonNodeMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.MultipleAmountInterpreter;
import io.sapl.test.dsl.interpreter.matcher.StringMatcherInterpreter;
import io.sapl.test.dsl.interpreter.matcher.ValMatcherInterpreter;
import io.sapl.test.dsl.lang.DefaultSaplTestInterpreter;
import io.sapl.test.utils.DocumentHelper;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;

public class TestBuilder {

    static final SaplTestInterpreter saplTestInterpreter = new DefaultSaplTestInterpreter();

    public static List<DynamicContainer> buildTests(final String filename) {
        final var testBuilder = getTestBuilder();

        if (filename == null) {
            throw new SaplTestException("provided filename is null");
        }

        final var input = DocumentHelper.findFileOnClasspath(filename);

        if (input == null) {
            throw new SaplTestException("file does not exist");
        }

        final var saplTest = saplTestInterpreter.loadAsResource(input);

        return testBuilder.buildTests(saplTest);
    }

    private static JUnitDynamicTestBuilder getTestBuilder() {
        final var objectMapper = new ObjectMapper();
        final var valInterpreter = new ValInterpreter(objectMapper);
        final var testFixtureBuilder = getTestFixtureBuilder(valInterpreter);

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

        final var whenStepBuilder = new DefaultWhenStepBuilder(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new DefaultExpectStepBuilder(authorizationSubscriptionInterpreter);
        final var verifyStepBuilder = new DefaultVerifyStepBuilder(expectInterpreter);


        final var testCaseBuilder = new TestCaseBuilder(testFixtureBuilder, whenStepBuilder, expectStepBuilder, verifyStepBuilder);

        return new DefaultTestBuilder(testCaseBuilder);
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
}
