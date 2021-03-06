package io.sapl.prp.index.canonical;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ConjunctiveClauseTest {

    @Test
    void testConstruction() {
        var clause = new ConjunctiveClause(new Literal(new Bool(true)));
        assertThat(clause, is(notNullValue()));
        assertThat(clause.size(), is(1));

        assertThrows(NullPointerException.class, () -> new ConjunctiveClause((Collection<Literal>) null));
        assertThrows(IllegalArgumentException.class, () -> new ConjunctiveClause(Collections.emptyList()));
    }

    @Test
    @SuppressWarnings("unlikely-arg-type")
    void testEquals() {
        var l1 = new Literal(new Bool(true));
        var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        var c4 = new ConjunctiveClause(l1, l1);
        var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(false)));
        var c6 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));

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
        var c1 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        var c2 = new ConjunctiveClause(new Literal(new Bool(true)));

        try (MockedStatic<ConjunctiveClauseReductionSupport> mock = mockStatic(ConjunctiveClauseReductionSupport.class)) {
            //            doNothing().when(mock).when(() -> ConjunctiveClauseReductionSupport.reduceConstants(anyList()));
            //            doNothing().when(mock).when(() -> ConjunctiveClauseReductionSupport.reduceFormula(anyList()));

            assertThat(c1.reduce().size(), is(c1.size()));

            mock.verify(() -> ConjunctiveClauseReductionSupport.reduceConstants(anyList()), times(1));
            mock.verify(() -> ConjunctiveClauseReductionSupport.reduceFormula(anyList()), times(1));

            assertThat(c2.reduce(), is(c2));
        }
    }

    @Test
    void testIsImmutable() {
        var c1 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        var boolMock = mock(Bool.class);
        when(boolMock.isImmutable()).thenReturn(false);
        var c2 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(boolMock));

        assertThat(c1.isImmutable(), is(true));
        assertThat(c2.isImmutable(), is(false));
    }

    @Test
    void testEvaluate() {
        var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        var c4 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(true)));

        assertThat(c1.evaluate(), is(true));
        assertThat(c2.evaluate(), is(true));
        assertThat(c3.evaluate(), is(false));
        assertThat(c4.evaluate(), is(false));
        assertThat(c5.evaluate(), is(false));
    }

    @Test
    void testIsSubsetOf() {
        var c1 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c2 = new ConjunctiveClause(new Literal(new Bool(true)));
        var c3 = new ConjunctiveClause(new Literal(new Bool(false)));
        var c4 = new ConjunctiveClause(new Literal(new Bool(true)), new Literal(new Bool(false)));
        var c5 = new ConjunctiveClause(new Literal(new Bool(false)), new Literal(new Bool(true)));

        assertThat(c1.isSubsetOf(c1), is(true));
        assertThat(c1.isSubsetOf(c2), is(true));
        assertThat(c1.isSubsetOf(c3), is(false));
        assertThat(c1.isSubsetOf(c4), is(true));
        assertThat(c1.isSubsetOf(c5), is(true));

    }

}
