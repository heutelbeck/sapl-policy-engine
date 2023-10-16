package io.sapl.test.dsl.interfaces;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;

public interface JUnitDynamicTestBuilder {
    List<DynamicTest> buildTests(String fileName);
}
