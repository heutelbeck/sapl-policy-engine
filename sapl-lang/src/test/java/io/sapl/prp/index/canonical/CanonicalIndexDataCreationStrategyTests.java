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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class CanonicalIndexDataCreationStrategyTests {

    @Test
    void testDefaultStrategy() {
        assertThat(new CanonicalIndexDataCreationStrategy(), notNullValue());
    }

    @Test
    void testCreatePredicateInfo() {
        var strategy = new CanonicalIndexDataCreationStrategy();

        var bool    = new Bool(true);
        var literal = new Literal(bool);
        var clause  = new ConjunctiveClause(literal);

        Map<Bool, PredicateInfo> boolToPredicateInfo       = new HashMap<>();
        Set<Bool>                negativesGroupedByFormula = new HashSet<>();
        Set<Bool>                positivesGroupedByFormula = new HashSet<>();

        strategy.createPredicateInfo(literal, clause, boolToPredicateInfo, negativesGroupedByFormula,
                positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfPositives(), is(1));
        assertThat(positivesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfPositives(), is(1));

        var negatedLiteral = new Literal(bool, true);
        strategy.createPredicateInfo(negatedLiteral, clause, boolToPredicateInfo, negativesGroupedByFormula,
                positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfNegatives(), is(1));
        assertThat(negativesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfNegatives(), is(1));

        strategy.createPredicateInfo(literal, clause, boolToPredicateInfo, negativesGroupedByFormula,
                positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfPositives(), is(2));
        assertThat(positivesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfPositives(), is(1));

        strategy.createPredicateInfo(negatedLiteral, clause, boolToPredicateInfo, negativesGroupedByFormula,
                positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfNegatives(), is(2));
        assertThat(negativesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfNegatives(), is(1));
    }

}
