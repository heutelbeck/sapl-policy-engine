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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class PredicateInfoTests {

    @Test
    void testConstruction() {
        final var predicateInfo = new PredicateInfo(new Predicate(new Bool(true)));

        assertThat(predicateInfo, is(notNullValue()));

        assertThat(predicateInfo.getPredicate(), is(notNullValue()));

        predicateInfo.setRelevance(0D);
        assertThat(predicateInfo.getRelevance(), is(0D));
        predicateInfo.setScore(0D);
        assertThat(predicateInfo.getScore(), is(0D));
    }

    @Test
    void testUtilityMethods() {
        final var predicateInfo = new PredicateInfo(new Predicate(new Bool(true)));

        predicateInfo.addUnsatisfiableConjunctionIfTrue(mock(ConjunctiveClause.class));
        predicateInfo.addUnsatisfiableConjunctionIfFalse(mock(ConjunctiveClause.class));
        assertThat(predicateInfo.getUnsatisfiableConjunctionsIfFalse(), not(is(empty())));
        assertThat(predicateInfo.getUnsatisfiableConjunctionsIfTrue(), not(is(empty())));

        assertThat(predicateInfo.getClauseRelevanceList(), is(empty()));
        predicateInfo.addToClauseRelevanceList(42D);
        assertThat(predicateInfo.getClauseRelevanceList().get(0), is(42D));

        assertThat(predicateInfo.getGroupedNumberOfNegatives(), is(0));
        assertThat(predicateInfo.getGroupedNumberOfPositives(), is(0));
        predicateInfo.incGroupedNumberOfNegatives();
        predicateInfo.incGroupedNumberOfPositives();
        assertThat(predicateInfo.getGroupedNumberOfNegatives(), is(1));
        assertThat(predicateInfo.getGroupedNumberOfPositives(), is(1));

        assertThat(predicateInfo.getNumberOfNegatives(), is(0));
        assertThat(predicateInfo.getNumberOfPositives(), is(0));
        predicateInfo.incNumberOfNegatives();
        predicateInfo.incNumberOfPositives();
        assertThat(predicateInfo.getNumberOfNegatives(), is(1));
        assertThat(predicateInfo.getNumberOfPositives(), is(1));
    }

    @Test
    void testCompareTo() {
        final var p1 = new PredicateInfo(new Predicate(new Bool(true)));
        final var p2 = new PredicateInfo(new Predicate(new Bool(true)));

        p1.setScore(0D);
        p2.setScore(0D);
        assertThat(p1.compareTo(p2), is(0));

        p2.setScore(1D);
        assertThat(p1.compareTo(p2), is(-1));

        p1.setScore(2D);
        assertThat(p1.compareTo(p2), is(1));
    }

    @Test
    @SuppressWarnings("unlikely-arg-type")
    void testEquals() {
        final var p1 = new PredicateInfo(new Predicate(new Bool(true)));
        final var p2 = new PredicateInfo(new Predicate(new Bool(true)));
        final var p3 = spy(p2);
        when(p3.getScore()).thenReturn(Double.MAX_VALUE);

        assertThat(p1.equals(p1), is(true));
        assertThat(p1.equals(null), is(false));
        assertThat(p1.equals(""), is(false));
        assertThat(p1.equals(p2), is(true));
        assertThat(p1.equals(p3), is(false));
    }

    @Test
    void testHashCode() {
        final var p1 = new PredicateInfo(new Predicate(new Bool(true)));
        assertThat(p1.hashCode(), not(is(0)));
    }

}
