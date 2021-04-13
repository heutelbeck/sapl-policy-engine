package io.sapl.prp.index.canonical;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DisjunctiveFormulaReductionSupportTest {

    @Test
    void testReduceConstants() {
        List<ConjunctiveClause> clauses = new ArrayList<>();

        assertThat(clauses.isEmpty(), is(true));
        DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
        assertThat(clauses.isEmpty(), is(true));

        var trueLiteral = new Literal(new Bool(true));
        var falseLiteral = new Literal(new Bool(false));
        var c1 = new ConjunctiveClause(trueLiteral);
        var c2 = new ConjunctiveClause(falseLiteral);

        clauses.add(c1);
        DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c2);
        DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c1);
        clauses.add(c2);
        DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c2);
        clauses.add(c2);
        DisjunctiveFormulaReductionSupport.reduceConstants(clauses);
        assertThat(clauses.size() == 1, is(true));
    }

    @Test
    void testReduceFormula() {
        List<ConjunctiveClause> clauses = new ArrayList<>();

        assertThat(clauses.isEmpty(), is(true));
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.isEmpty(), is(true));


        var trueLiteral = new Literal(new Bool(true));
        var falseLiteral = new Literal(new Bool(false));
        var trueNegatedLiteral = new Literal(new Bool(true), true);
        var falseNegatedLiteral = new Literal(new Bool(false), true);
        var c1 = new ConjunctiveClause(trueLiteral);
        var c2 = new ConjunctiveClause(falseLiteral);
        var c3 = new ConjunctiveClause(trueNegatedLiteral);
        var c4 = new ConjunctiveClause(falseNegatedLiteral);
        var c5 = new ConjunctiveClause(trueLiteral, trueNegatedLiteral);

        clauses.add(c1);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c2);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c1);
        clauses.add(c1);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c2);
        clauses.add(c2);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();


        clauses.add(c1);
        clauses.add(c3);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 2, is(true));
        clauses.clear();


        clauses.add(c1);
        clauses.add(c4);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 2, is(true));
        clauses.clear();

        clauses.add(c3);
        clauses.add(c4);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 2, is(true));
        clauses.clear();

        clauses.add(c1);
        clauses.add(c5);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

        clauses.add(c5);
        clauses.add(c1);
        DisjunctiveFormulaReductionSupport.reduceFormula(clauses);
        assertThat(clauses.size() == 1, is(true));
        clauses.clear();

    }
}
