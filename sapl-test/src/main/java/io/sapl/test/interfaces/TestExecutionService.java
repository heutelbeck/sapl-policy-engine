package io.sapl.test.interfaces;

import org.junit.platform.launcher.TestExecutionListener;

public interface TestExecutionService {
    void execute(final String className, final TestExecutionListener testExecutionListener);
}
