package io.sapl.test.setup;

import io.sapl.test.interpreter.SaplTestInterpreterDefaultImpl;
import io.sapl.test.services.ExpectStepBuilderDefaultImpl;
import io.sapl.test.services.GivenStepBuilderServiceDefaultImpl;
import io.sapl.test.services.TestBuilderServiceDefaultImpl;
import io.sapl.test.services.VerifyStepBuilderServiceDefaultImpl;
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
        final var testProvider = new TestProviderDefaultImpl();
        final var givenStepBuilder = new GivenStepBuilderServiceDefaultImpl(pip);
        final var expectStepBuilder = new ExpectStepBuilderDefaultImpl();
        final var verifyStepBuilder = new VerifyStepBuilderServiceDefaultImpl();
        final var saplInterpreter = new SaplTestInterpreterDefaultImpl();

        new TestBuilderServiceDefaultImpl(testProvider, givenStepBuilder, expectStepBuilder, verifyStepBuilder, saplInterpreter).buildTest();
    }

    private static TestExecutionSummary executeTests(String className, SummaryGeneratingListener testExecutionListener) {
        final var executor = new TestExecutionServiceDefaultImpl();
        executor.execute(className, testExecutionListener);
        return testExecutionListener.getSummary();
    }
}
