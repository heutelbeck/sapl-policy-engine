/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.junit.jupiter.api.Test;

class DisjunctiveFormulaReductionSupportTest {

	@Test
	void testReduceConstants() {
		List<ConjunctiveClause> clauses = new ArrayList<>();

		assertThat(clauses.isEmpty(), is(Boolean.TRUE));
		DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
		assertThat(clauses.isEmpty(), is(Boolean.TRUE));

		var trueLiteral  = new Literal(new Bool(true));
		var falseLiteral = new Literal(new Bool(false));
		var c1           = new ConjunctiveClause(trueLiteral);
		var c2           = new ConjunctiveClause(falseLiteral);

		clauses.add(c1);
		DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c2);
		DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c1);
		clauses.add(c2);
		DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c2);
		clauses.add(c2);
		DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
	}

	@Test
	void testReduceFormula() {
		List<ConjunctiveClause> clauses = new ArrayList<>();

		assertThat(clauses.isEmpty(), is(Boolean.TRUE));
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.isEmpty(), is(Boolean.TRUE));

		var trueLiteral         = new Literal(new Bool(true));
		var falseLiteral        = new Literal(new Bool(false));
		var trueNegatedLiteral  = new Literal(new Bool(true), true);
		var falseNegatedLiteral = new Literal(new Bool(false), true);
		var c1                  = new ConjunctiveClause(trueLiteral);
		var c2                  = new ConjunctiveClause(falseLiteral);
		var c3                  = new ConjunctiveClause(trueNegatedLiteral);
		var c4                  = new ConjunctiveClause(falseNegatedLiteral);
		var c5                  = new ConjunctiveClause(trueLiteral, trueNegatedLiteral);

		clauses.add(c1);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c2);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c1);
		clauses.add(c1);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c2);
		clauses.add(c2);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c1);
		clauses.add(c3);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 2, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c1);
		clauses.add(c4);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 2, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c3);
		clauses.add(c4);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 2, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c1);
		clauses.add(c5);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();

		clauses.add(c5);
		clauses.add(c1);
		DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
		assertThat(clauses.size() == 1, is(Boolean.TRUE));
		clauses.clear();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testReduceFormulaStep() {
		var clauseMock = mock(ConjunctiveClause.class);
		when(clauseMock.isSubsetOf(any())).thenReturn(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);
		var clauseMock2 = mock(ConjunctiveClause.class);
		when(clauseMock2.isSubsetOf(any())).thenReturn(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE);

		var pointerMock = mock(ListIterator.class);
		var forwardMock = mock(ListIterator.class);
		when(forwardMock.next()).thenReturn(clauseMock2, null, clauseMock2, clauseMock2);
		when(forwardMock.hasNext()).thenReturn(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);

		var listMock = mock(List.class);
		when(listMock.listIterator(anyInt())).thenReturn(forwardMock);

		DisjunctiveFormulaReductionSupport.reduceFormulaStep(listMock, pointerMock, clauseMock);
	}

}
