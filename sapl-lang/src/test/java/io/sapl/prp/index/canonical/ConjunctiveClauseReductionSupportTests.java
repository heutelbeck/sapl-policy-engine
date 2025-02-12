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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConjunctiveClauseReductionSupportTests {

    @Test
    void testReduceConstants() {
        List<Literal> literals = new ArrayList<>();

        assertThat(literals.isEmpty(), is(true));
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.isEmpty(), is(true));

        final var trueLiteral  = new Literal(new Bool(true));
        final var falseLiteral = new Literal(new Bool(false));

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));

        literals.add(falseLiteral);
        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));
    }

    @Test
    void testReduceFormula() {
        List<Literal> literals = new ArrayList<>();

        assertThat(literals.isEmpty(), is(Boolean.TRUE));
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.isEmpty(), is(Boolean.TRUE));

        final var trueLiteral         = new Literal(new Bool(true));
        final var falseLiteral        = new Literal(new Bool(false));
        final var trueNegatedLiteral  = new Literal(new Bool(true), true);
        final var falseNegatedLiteral = new Literal(new Bool(false), true);

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));
        literals.clear();

        literals.add(falseLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));
        literals.clear();

        literals.add(trueLiteral);
        literals.add(falseLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 2, is(Boolean.TRUE));
        literals.clear();

        literals.add(trueLiteral);
        literals.add(trueNegatedLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(Boolean.TRUE));
        literals.clear();

        literals.add(trueNegatedLiteral);
        literals.add(trueNegatedLiteral);
        literals.add(falseNegatedLiteral);
        literals.add(falseNegatedLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 2, is(Boolean.TRUE));
        literals.clear();

    }

}
