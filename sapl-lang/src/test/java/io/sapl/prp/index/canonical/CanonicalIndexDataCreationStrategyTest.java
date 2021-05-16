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

import lombok.val;

class CanonicalIndexDataCreationStrategyTest {

    @Test
    void testDefaultStrategy() {
        assertThat(new CanonicalIndexDataCreationStrategy(), notNullValue());
    }

    @Test
    void testCreatePredicateInfo() {
        var strategy = new CanonicalIndexDataCreationStrategy();

        val bool = new Bool(true);
        val literal = new Literal(bool);
        val clause = new ConjunctiveClause(literal);

        Map<Bool, PredicateInfo> boolToPredicateInfo = new HashMap<>();
        Set<Bool> negativesGroupedByFormula = new HashSet<>();
        Set<Bool> positivesGroupedByFormula = new HashSet<>();

        strategy.createPredicateInfo(literal, clause, boolToPredicateInfo, negativesGroupedByFormula, positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfPositives(), is(1));
        assertThat(positivesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfPositives(), is(1));

        val negatedLiteral = new Literal(bool, true);
        strategy.createPredicateInfo(negatedLiteral, clause, boolToPredicateInfo, negativesGroupedByFormula, positivesGroupedByFormula, clause
                .size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfNegatives(), is(1));
        assertThat(negativesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfNegatives(), is(1));


        strategy.createPredicateInfo(literal, clause, boolToPredicateInfo, negativesGroupedByFormula, positivesGroupedByFormula, clause.size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfPositives(), is(2));
        assertThat(positivesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfPositives(), is(1));

        strategy.createPredicateInfo(negatedLiteral, clause, boolToPredicateInfo, negativesGroupedByFormula, positivesGroupedByFormula, clause
                .size());
        assertThat(boolToPredicateInfo.get(bool).getNumberOfNegatives(), is(2));
        assertThat(negativesGroupedByFormula, Matchers.contains(bool));
        assertThat(boolToPredicateInfo.get(bool).getGroupedNumberOfNegatives(), is(1));
    }
}
