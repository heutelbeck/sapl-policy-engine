/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConjunctiveClauseTests {

    @Test
    void testConstruction() {
        final var clause = new ConjunctiveClause(new Literal(new Bool(true)));
        assertThat(clause, is(notNullValue()));
        assertThat(clause.size(), is(1));

        assertThrows(NullPointerException.class, () -> new ConjunctiveClause((Collection<Literal>) null));

        Collection<Literal> emptyList = Collections.emptyList();
        assertThrows(IllegalArgumentException.class, () -> new ConjunctiveClause(emptyList));
    }

    @Test
    @SuppressWarnings("unlikely-arg-type")
    void testEquals() {
        final var l1 = new Literal(new Bool(true));
        final var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        final var c4 = new ConjunctiveClause(l1, l1);
        final var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(false)));
        final var c6 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));

        assertThat(c1.equals(c1), is(true));
        assertThat(c1.equals(c2), is(true)); // true, true
        assertThat(c1.equals(c3), is(false));
        assertThat(c1.equals(c4), is(false));
        assertThat(c4.equals(c5), is(false));
        assertThat(c5.equals(c4), is(false));
        assertThat(c4.equals(c6), is(false));
        assertThat(c6.equals(c4), is(false));

        assertThat(c1.equals(null), is(false));
        assertThat(c1.equals(""), is(false));
    }

    @Test
    void testReduce() {
        final var c1 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        final var c2 = new ConjunctiveClause(new Literal(new Bool(true)));

        try (MockedStatic<ConjunctiveClauseReductionSupport> mock = mockStatic(
                ConjunctiveClauseReductionSupport.class)) {

            assertThat(c1.reduce().size(), is(c1.size()));

            mock.verify(() -> ConjunctiveClauseReductionSupport.reduceConstants(anyList()), times(1));
            mock.verify(() -> ConjunctiveClauseReductionSupport.reduceFormula(anyList()), times(1));

            assertThat(c2.reduce(), is(c2));
        }
    }

    @Test
    void testIsImmutable() {
        final var c1       = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        final var boolMock = mock(Bool.class);
        when(boolMock.isImmutable()).thenReturn(Boolean.FALSE);
        final var c2 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(boolMock));

        assertThat(c1.isImmutable(), is(true));
        assertThat(c2.isImmutable(), is(false));
    }

    @Test
    void testEvaluate() {
        final var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        final var c4 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        final var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(true)));

        assertThat(c1.evaluate(), is(true));
        assertThat(c2.evaluate(), is(true));
        assertThat(c3.evaluate(), is(false));
        assertThat(c4.evaluate(), is(false));
        assertThat(c5.evaluate(), is(false));
    }

    @Test
    void testIsSubsetOf() {
        final var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        final var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        final var c4 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        final var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(true)));

        assertThat(c1.isSubsetOf(c1), is(true));
        assertThat(c1.isSubsetOf(c2), is(true));
        assertThat(c1.isSubsetOf(c3), is(false));
        assertThat(c1.isSubsetOf(c4), is(true));
        assertThat(c1.isSubsetOf(c5), is(true));

    }

}
