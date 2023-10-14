package io.sapl.test.setup;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import io.sapl.test.interpreter.SaplTestInterpreterDefaultImpl;
import io.sapl.test.services.*;
import io.sapl.test.services.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.services.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import io.sapl.test.services.matcher.AuthorizationDecisionMatcherInterpreter;
import io.sapl.test.services.matcher.JsonNodeMatcherInterpreter;
import io.sapl.test.services.matcher.StringMatcherInterpreter;
import io.sapl.test.services.matcher.ValMatcherInterpreter;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;

public class TestBuilder {
    public static List<DynamicTest> buildTests(final Object pip, final String filename) {
        final var objectMapper = new ObjectMapper();
        final var valInterpreter = new ValInterpreter(objectMapper);
        final var testFixtureBuilder = getTestFixtureBuilder(pip, valInterpreter);

        final var stringMatcherInterpreter = new StringMatcherInterpreter();
        final var jsonNodeMatcherInterpreter = new JsonNodeMatcherInterpreter(stringMatcherInterpreter);

        final var matcherInterpreter = new ValMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter, stringMatcherInterpreter);

        final var attributeInterpreter = new AttributeInterpreter(valInterpreter, matcherInterpreter);
        final var functionInterpreter = new FunctionInterpreter(valInterpreter, matcherInterpreter);
        final var authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(valInterpreter, objectMapper);
        final var authorizationSubscriptionInterpreter = new AuthorizationSubscriptionInterpreter(valInterpreter);
        final var authorizationDecisionMatcherInterpreter = new AuthorizationDecisionMatcherInterpreter(valInterpreter, jsonNodeMatcherInterpreter);
        final var expectInterpreter = new ExpectInterpreter(valInterpreter, authorizationDecisionInterpreter, authorizationDecisionMatcherInterpreter);

        final var givenStepBuilder = new WhenStepBuilderServiceDefaultImpl(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new ExpectStepBuilderDefaultImpl(authorizationSubscriptionInterpreter);
        final var verifyStepBuilder = new VerifyStepBuilderServiceDefaultImpl(expectInterpreter);
        final var saplInterpreter = new SaplTestInterpreterDefaultImpl();

        return new TestBuilderServiceDefaultImpl(testFixtureBuilder, givenStepBuilder, expectStepBuilder, verifyStepBuilder, saplInterpreter)
                .buildTests(filename);
    }

    private static TestFixtureBuilder getTestFixtureBuilder(final Object pip, final ValInterpreter valInterpreter) {
        final var pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();

        final var saplUnitTestFixtureConstructorWrapper = new SaplUnitTestFixtureConstructorWrapper();
        final var saplIntegrationTestFixtureConstructorWrapper = new SaplIntegrationTestFixtureConstructorWrapper();
        final var testSuiteInterpreter = new TestSuiteInterpreter(valInterpreter, pdpCombiningAlgorithmInterpreter, saplUnitTestFixtureConstructorWrapper, saplIntegrationTestFixtureConstructorWrapper);

        return new TestFixtureBuilder(pip, testSuiteInterpreter);
    }
}
