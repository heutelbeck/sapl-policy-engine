package io.sapl.prp.index.canonical;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import reactor.core.publisher.Flux;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BoolTest {

    Bool constantBool;
    Bool expressionBool;

    @BeforeEach
    void setUp() {
        constantBool = new Bool(false);

        var expressionMock = mock(Expression.class, RETURNS_DEEP_STUBS);
        when(expressionMock.evaluate(any(), any())).thenReturn(Flux.just(Val.TRUE));
        expressionBool = new Bool(expressionMock, Collections.emptyMap());
    }

    @Test
    void constructUsingConstantTest() {
        assertThat(constantBool, is(notNullValue()));
        assertThat(constantBool.isImmutable(), is(true));
        assertThat(constantBool.hashCode(), not(is(0)));
    }

    @Test
    void constructUsingExpressionTest() {
        assertThat(expressionBool, is(notNullValue()));
        assertThat(expressionBool.isImmutable(), is(false));
    }


    @Test
    void equalsTest() {
        assertThat(constantBool.equals(constantBool), is(true));
        assertThat(expressionBool.equals(expressionBool), is(true));
        assertThat(expressionBool.equals(constantBool), is(false));

        assertThat(constantBool.equals(null), is(false));
        assertThat(constantBool.equals(""), is(false));

        assertThat(constantBool.equals(new Bool(false)), is(true));
        assertThat(constantBool.equals(new Bool(true)), is(false));

        try (MockedStatic<EquivalenceAndHashUtil> mock = mockStatic(EquivalenceAndHashUtil.class)) {
            mock.when(() -> EquivalenceAndHashUtil.semanticHash(any(), any())).thenReturn(42);

            var expressionMock = mock(Expression.class, RETURNS_DEEP_STUBS);
            var otherExpressionBool = new Bool(expressionMock, Collections.emptyMap());

            assertThat(expressionBool.equals(otherExpressionBool), is(false));
        }

    }

    @Test
    void evaluateTest() {
        assertThat(constantBool.evaluate(), is(false));
        assertThrows(IllegalStateException.class, expressionBool::evaluate);

        var contextMock = mock(EvaluationContext.class);
        when(contextMock.withImports(any())).thenReturn(contextMock);
        var result = expressionBool.evaluate(contextMock).block();

        assertThat(result.isBoolean(), is(true));
        assertThat(result.get().asBoolean(), is(true));

    }

}
