package io.sapl.broker.impl;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class TimeOutWrapperTests {

    private static final Val TIME_OUT = Val.error("time out");
    private static final Val EMPTY    = Val.error("empty");

    @Test
    void whenEmptyFluxThenEmpty() {
        var source  = Flux.<Val>empty();
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(2500L), TIME_OUT, EMPTY);
        StepVerifier.withVirtualTime(() -> wrapped).thenAwait(Duration.ofMillis(10L)).expectNext(EMPTY)
                .verifyComplete();
    }

    @Test
    void whenFirstElementsAfterTimeOutThenSendTimeOutAndThenTheRestOfTheFlux() {
        var source  = Flux.just(Val.of(1), Val.of(2), Val.of(3)).delayElements(Duration.ofMillis(5L));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(TIME_OUT, Val.of(1), Val.of(2), Val.of(3)).verifyComplete();
    }

    @Test
    void whenEmptyAfterTimeoutThenSendTimeOutAndEmpty() {
        var source  = Flux.just(Val.of(1)).delayElements(Duration.ofMillis(5L)).filter(v -> false);
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(TIME_OUT, EMPTY).verifyComplete();
    }

    @Test
    void whenElementAndClosedThenNoDelayTillWrappedClosed() {
        var source  = Flux.just(Val.of(0));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMinutes(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(Val.of(0)).expectComplete().verify(Duration.ofMillis(10L));
    }

    @Test
    void whenErrorThenNoDelayTillWrappedClosed() {
        var source  = Flux.<Val>error(new IllegalStateException("testing"));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMinutes(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectError().verify(Duration.ofMillis(10L));
    }

}
