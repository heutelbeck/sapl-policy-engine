package io.sapl.test.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.interpreter.SaplTestInterpreterDefaultImpl;
import io.sapl.test.services.*;
import io.sapl.test.services.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.services.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;

public class TestBuilder {
    public static List<DynamicTest> buildTests(final Object pip, final String filename) {
        final var objectMapper = new ObjectMapper();
        final var valInterpreter = new ValInterpreter(objectMapper);

        final var saplUnitTestFixtureConstructorWrapper = new SaplUnitTestFixtureConstructorWrapper();
        final var saplIntegrationTestFixtureConstructorWrapper = new SaplIntegrationTestFixtureConstructorWrapper();
        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, saplUnitTestFixtureConstructorWrapper, saplIntegrationTestFixtureConstructorWrapper);

        final var testFixtureBuilder = new TestFixtureBuilder(pip, testSuiteInterpreter);

        final var matcherInterpreter = new MatcherInterpreter(valInterpreter);

        final var attributeInterpreter = new AttributeInterpreter(valInterpreter, matcherInterpreter);
        final var functionInterpreter = new FunctionInterpreter(valInterpreter, matcherInterpreter);
        final var authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(valInterpreter, objectMapper);
        final var authorizationSubscriptionInterpreter = new AuthorizationSubscriptionInterpreter(valInterpreter);
        final var expectInterpreter = new ExpectInterpreter(valInterpreter, authorizationDecisionInterpreter);

        final var givenStepBuilder = new WhenStepBuilderServiceDefaultImpl(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new ExpectStepBuilderDefaultImpl(authorizationSubscriptionInterpreter);
        final var verifyStepBuilder = new VerifyStepBuilderServiceDefaultImpl(expectInterpreter);
        final var saplInterpreter = new SaplTestInterpreterDefaultImpl();

        return new TestBuilderServiceDefaultImpl(testFixtureBuilder, givenStepBuilder, expectStepBuilder, verifyStepBuilder, saplInterpreter)
                .buildTests(filename);
    }
}
