package io.sapl.prp.index.canonical;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class LiteralTest {

    @Test
    void isImmutableTest() {
        assertThat(new Literal(new Bool(false)).isImmutable(), is(true));
    }

    @Test
    void evaluateTest() {
        assertThat(new Literal(new Bool(false)).evaluate(), is(false));
        assertThat(new Literal(new Bool(false), true).evaluate(), is(true));
    }

    @Test
    void negateTest() {
        var literal = new Literal(new Bool(false));
        var negated = literal.negate();
        assertThat(literal.evaluate(), is(!negated.evaluate()));
    }

    @Test
    void sharesBoolTest() {
        var trueLiteral = new Literal(new Bool(true));
        var falseLiteral = new Literal(new Bool(false));

        assertThat(trueLiteral.sharesBool(trueLiteral), is(true));
        assertThat(trueLiteral.sharesBool(falseLiteral), is(false));
    }

    @Test
    void sharesNegationTest() {
        var literal = new Literal(new Bool(true), false);
        var negatedLiteral = new Literal(new Bool(false), true);

        assertThat(literal.sharesNegation(literal), is(true));
        assertThat(negatedLiteral.sharesNegation(negatedLiteral), is(true));
        assertThat(literal.sharesNegation(negatedLiteral), is(false));
    }

    @Test
    void equalsTest() {
        var literal = new Literal(new Bool(false), true);
        var literalMock = mock(Literal.class);

        assertThat(literal.equals(literal), is(true));
        assertThat(literal.equals(null), is(false));
        assertThat(literal.equals(""), is(false));
        assertThat(literal.equals(literalMock), is(false));
        assertThat(literal.equals(new Literal(new Bool(false), false)), is(false));
        assertThat(literal.equals(new Literal(new Bool(true), true)), is(false));
        assertThat(literal.equals(new Literal(new Bool(false), true)), is(true));
    }
}
