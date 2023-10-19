package io.sapl.test.dsl.interfaces;

import java.util.List;
import org.junit.jupiter.api.DynamicContainer;

public interface JUnitDynamicTestBuilder {
    List<DynamicContainer> buildTests(String fileName);
}
