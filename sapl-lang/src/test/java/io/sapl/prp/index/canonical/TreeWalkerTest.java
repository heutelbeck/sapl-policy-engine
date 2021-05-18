package io.sapl.prp.index.canonical;

import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FilterComponent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class TreeWalkerTest {

    static class BasicGroupMockBuilder {

        BasicGroup mock = mock(BasicGroup.class, RETURNS_DEEP_STUBS);

        static BasicGroupMockBuilder newBuilder() {
            return new BasicGroupMockBuilder();
        }

        BasicGroup build() {
            return mock;
        }

        private BasicGroupMockBuilder noFilter() {
            when(mock.getFilter()).thenReturn(null);
            return this;
        }

        private BasicGroupMockBuilder withFilter() {
            when(mock.getFilter()).thenReturn(mock(FilterComponent.class));
            return this;
        }

        private BasicGroupMockBuilder noSteps() {
            when(mock.getSteps().isEmpty()).thenReturn(true);
            return this;
        }

        private BasicGroupMockBuilder withSteps() {
            when(mock.getSteps().isEmpty()).thenReturn(false);
            return this;
        }

        private BasicGroupMockBuilder noTemplate() {
            when(mock.getSubtemplate()).thenReturn(null);
            return this;
        }

        private BasicGroupMockBuilder withTemplate() {
            when(mock.getSubtemplate()).thenReturn(mock(Expression.class));
            return this;
        }

    }

    @Test
    void traverse_should_call_walk_when_node_has_no_filters_steps_and_subtemplate() {
        var allFalse = BasicGroupMockBuilder.newBuilder()
                .noFilter().noSteps().noTemplate().build();
        var allTrue = BasicGroupMockBuilder.newBuilder()
                .withFilter().withSteps().withTemplate().build();
        var noTemplate = BasicGroupMockBuilder.newBuilder()
                .withFilter().withSteps().noTemplate().build();
        var noSteps = BasicGroupMockBuilder.newBuilder()
                .withFilter().noSteps().withTemplate().build();
        var noFilter = BasicGroupMockBuilder.newBuilder()
                .noFilter().withSteps().withTemplate().build();
        var onlyTemplate = BasicGroupMockBuilder.newBuilder()
                .noFilter().noSteps().withTemplate().build();
        var onlySteps = BasicGroupMockBuilder.newBuilder()
                .noFilter().withSteps().noTemplate().build();
        var onlyFilter = BasicGroupMockBuilder.newBuilder()
                .withFilter().noSteps().noTemplate().build();


        verifyTraverseCalledWalk(allFalse);
        verifyTraverseCalledEndRecursion(allTrue);
        verifyTraverseCalledEndRecursion(onlyFilter);
        verifyTraverseCalledEndRecursion(onlySteps);
        verifyTraverseCalledEndRecursion(onlyTemplate);
        verifyTraverseCalledEndRecursion(noFilter);
        verifyTraverseCalledEndRecursion(noSteps);
        verifyTraverseCalledEndRecursion(noTemplate);

    }

    private void verifyTraverseCalledWalk(BasicGroup group) {
        try (MockedStatic<TreeWalker> mock = mockStatic(TreeWalker.class)) {
            mock.when(() -> TreeWalker.walk(any(), any()))
                    .thenReturn(mock(DisjunctiveFormula.class));
            mock.when(() -> TreeWalker.traverse(any(), any()))
                    .thenCallRealMethod();
            mock.when(() -> TreeWalker.endRecursion(any(), any()))
                    .thenReturn(mock(DisjunctiveFormula.class));

            TreeWalker.traverse(group, Collections.emptyMap());

            mock.verify(() -> TreeWalker.traverse(any(), any()));
            mock.verify(() -> TreeWalker.walk(any(), any()));
        }
    }

    private void verifyTraverseCalledEndRecursion(BasicGroup group) {
        try (MockedStatic<TreeWalker> mock = mockStatic(TreeWalker.class)) {
            mock.when(() -> TreeWalker.walk(any(), any()))
                    .thenReturn(mock(DisjunctiveFormula.class));
            mock.when(() -> TreeWalker.traverse(any(), any()))
                    .thenCallRealMethod();
            mock.when(() -> TreeWalker.endRecursion(any(), any()))
                    .thenReturn(mock(DisjunctiveFormula.class));

            TreeWalker.traverse(group, Collections.emptyMap());

            mock.verify(() -> TreeWalker.traverse(any(), any()));
            mock.verify(() -> TreeWalker.endRecursion(any(), any()));
        }
    }


}
