package io.sapl.prp.index.canonical;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Mono;

class PredicateTest {

    @Test
    void testConstruction() {
        var predicate = new Predicate(new Bool(true));
        assertThat(predicate, is(notNullValue()));
        assertThat(predicate.getBool().evaluate(), is(true));
    }

    @Test
    void testEvaluate() {
        var contextMock = mock(EvaluationContext.class);
        var boolMock = mock(Bool.class);
        when(boolMock.evaluate(any())).thenReturn(Mono.just(Val.TRUE));

        var predicate = new Predicate(boolMock);
        var result = predicate.evaluate(contextMock).block();
        assertThat(result.getBoolean(), is(true));
    }
}
