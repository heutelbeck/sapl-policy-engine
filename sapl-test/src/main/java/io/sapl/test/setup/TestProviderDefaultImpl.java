package io.sapl.test.setup;

import io.sapl.test.interfaces.TestProvider;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

class TestProviderDefaultImpl implements TestProvider {

    private static final List<DynamicTest> dynamicTests = new LinkedList<>();

    @TestFactory
    Collection<DynamicTest> dynamicTestCollection() {
        return dynamicTests;
    }

    @Override
    public void addTestCase(String title, Executable executable) {
        dynamicTests.add(DynamicTest.dynamicTest(title, executable));
    }
}
