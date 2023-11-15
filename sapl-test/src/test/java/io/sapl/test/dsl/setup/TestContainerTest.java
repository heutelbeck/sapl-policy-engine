package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sapl.test.dsl.interfaces.TestNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestContainerTest {

    @Test
    void from_buildsTestContainerWithGivenIdentifierAndTestNodes_returnsTestContainer() {
        final var testNodes = List.<TestNode>of();

        final var container = TestContainer.from("identifier", testNodes);

        assertEquals("identifier", container.getIdentifier());
        assertEquals(testNodes, container.getTestNodes());
    }
}