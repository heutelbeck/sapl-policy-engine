package io.sapl.test.dsl.setup;

import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TestContainer implements TestNode {

    private final String identifier;
    @Getter
    private final List<? extends TestNode> testNodes;

    public static TestContainer from(final String identifier, final List<? extends TestNode> testNodes) {
        return new TestContainer(identifier, testNodes);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
