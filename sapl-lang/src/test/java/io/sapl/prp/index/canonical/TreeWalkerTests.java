/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.index.canonical;

import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FilterComponent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TreeWalkerTests {

    static class BasicGroupMockBuilder {

        final BasicGroup mock = mock(BasicGroup.class, RETURNS_DEEP_STUBS);

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
            when(mock.getSteps().isEmpty()).thenReturn(Boolean.TRUE);
            return this;
        }

        private BasicGroupMockBuilder withSteps() {
            when(mock.getSteps().isEmpty()).thenReturn(Boolean.FALSE);
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
    void traverse_should_call_walk_when_node_has_no_filters_steps_and_subTemplate() {
        final var allFalse     = BasicGroupMockBuilder.newBuilder().noFilter().noSteps().noTemplate().build();
        final var allTrue      = BasicGroupMockBuilder.newBuilder().withFilter().withSteps().withTemplate().build();
        final var noTemplate   = BasicGroupMockBuilder.newBuilder().withFilter().withSteps().noTemplate().build();
        final var noSteps      = BasicGroupMockBuilder.newBuilder().withFilter().noSteps().withTemplate().build();
        final var noFilter     = BasicGroupMockBuilder.newBuilder().noFilter().withSteps().withTemplate().build();
        final var onlyTemplate = BasicGroupMockBuilder.newBuilder().noFilter().noSteps().withTemplate().build();
        final var onlySteps    = BasicGroupMockBuilder.newBuilder().noFilter().withSteps().noTemplate().build();
        final var onlyFilter   = BasicGroupMockBuilder.newBuilder().withFilter().noSteps().noTemplate().build();

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
            mock.when(() -> TreeWalker.walk(any())).thenReturn(mock(DisjunctiveFormula.class));
            mock.when(() -> TreeWalker.traverse(any())).thenCallRealMethod();
            mock.when(() -> TreeWalker.endRecursion(any())).thenReturn(mock(DisjunctiveFormula.class));

            TreeWalker.traverse(group);

            mock.verify(() -> TreeWalker.traverse(any()));
            mock.verify(() -> TreeWalker.walk(any()));
        }
    }

    private void verifyTraverseCalledEndRecursion(BasicGroup group) {
        try (MockedStatic<TreeWalker> mock = mockStatic(TreeWalker.class)) {
            mock.when(() -> TreeWalker.walk(any())).thenReturn(mock(DisjunctiveFormula.class));
            mock.when(() -> TreeWalker.traverse(any())).thenCallRealMethod();
            mock.when(() -> TreeWalker.endRecursion(any())).thenReturn(mock(DisjunctiveFormula.class));

            TreeWalker.traverse(group);

            mock.verify(() -> TreeWalker.traverse(any()));
            mock.verify(() -> TreeWalker.endRecursion(any()));
        }
    }

}
