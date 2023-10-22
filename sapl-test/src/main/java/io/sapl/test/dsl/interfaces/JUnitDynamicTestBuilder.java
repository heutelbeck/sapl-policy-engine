package io.sapl.test.dsl.interfaces;

import io.sapl.test.grammar.sAPLTest.SAPLTest;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;

public interface JUnitDynamicTestBuilder {
    List<DynamicContainer> buildTests(SAPLTest saplTest);
}
