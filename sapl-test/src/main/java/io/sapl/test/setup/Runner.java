package io.sapl.test.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.test.interpreter.SaplTestInterpreterDefaultImpl;
import io.sapl.test.services.*;
import java.io.PrintWriter;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public class Runner {
    public static void run(Object pip) {

        final var className = TestProviderDefaultImpl.class.getName();
        SummaryGeneratingListener testExecutionListener = new SummaryGeneratingListener();

        buildTests(pip);

        final var summary = executeTests(className, testExecutionListener);
        summary.printTo(new PrintWriter(System.out));
    }

    private static void buildTests(Object pip) {
        final var objectMapper = new ObjectMapper();
        final var testProvider = new TestProviderDefaultImpl();
        final var testFixtureBuilder = new TestFixtureBuilder(pip);

        final var valInterpreter = new ValInterpreter();
        final var matcherInterpreter = new MatcherInterpreter(valInterpreter);

        final var attributeInterpreter = new AttributeInterpreter(valInterpreter, matcherInterpreter);
        final var functionInterpreter = new FunctionInterpreter(valInterpreter, matcherInterpreter);
        final var authorizationDecisionInterpreter = new AuthorizationDecisionInterpreter(objectMapper);
        final var expectInterpreter = new ExpectInterpreter(valInterpreter, authorizationDecisionInterpreter);

        final var givenStepBuilder = new GivenStepBuilderServiceDefaultImpl(functionInterpreter, attributeInterpreter);
        final var expectStepBuilder = new ExpectStepBuilderDefaultImpl(objectMapper);
        final var verifyStepBuilder = new VerifyStepBuilderServiceDefaultImpl(expectInterpreter);
        final var saplInterpreter = new SaplTestInterpreterDefaultImpl();

        new TestBuilderServiceDefaultImpl(testProvider, testFixtureBuilder, givenStepBuilder, expectStepBuilder, verifyStepBuilder, saplInterpreter)
                .buildTest("test.sapltest");
    }

    private static TestExecutionSummary executeTests(String className, SummaryGeneratingListener testExecutionListener) {
        final var executor = new TestExecutionServiceDefaultImpl();
        executor.execute(className, testExecutionListener);
        return testExecutionListener.getSummary();
    }
}
