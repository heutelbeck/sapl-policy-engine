package io.sapl.prp.index.canonical;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConjunctiveClauseReductionSupportTest {

    @Test
    void testReduceConstants() {
        List<Literal> literals = new ArrayList<>();

        assertThat(literals.isEmpty(), is(true));
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.isEmpty(), is(true));

        var trueLiteral = new Literal(new Bool(true));
        var falseLiteral = new Literal(new Bool(false));

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(true));

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(true));

        literals.add(falseLiteral);
        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceConstants(literals);
        assertThat(literals.size() == 1, is(true));
    }

    @Test
    void testReduceFormula() {
        List<Literal> literals = new ArrayList<>();

        assertThat(literals.isEmpty(), is(true));
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.isEmpty(), is(true));


        var trueLiteral = new Literal(new Bool(true));
        var falseLiteral = new Literal(new Bool(false));
        var trueNegatedLiteral = new Literal(new Bool(true),true);
        var falseNegatedLiteral = new Literal(new Bool(false),true);

        literals.add(trueLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(true));
        literals.clear();


        literals.add(falseLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(true));
        literals.clear();


        literals.add(trueLiteral);
        literals.add(falseLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 2, is(true));
        literals.clear();


        literals.add(trueLiteral);
        literals.add(trueNegatedLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 1, is(true));
        literals.clear();


        literals.add(trueNegatedLiteral);
        literals.add(trueNegatedLiteral);
        literals.add(falseNegatedLiteral);
        literals.add(falseNegatedLiteral);
        ConjunctiveClauseReductionSupport.reduceFormula(literals);
        assertThat(literals.size() == 2, is(true));
        literals.clear();

    }
}
