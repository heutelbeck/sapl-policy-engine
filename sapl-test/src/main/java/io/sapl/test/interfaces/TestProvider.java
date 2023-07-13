package io.sapl.test.interfaces;

import org.junit.jupiter.api.function.Executable;

public interface TestProvider {
    void addTestCase(String title, Executable executable);
}
