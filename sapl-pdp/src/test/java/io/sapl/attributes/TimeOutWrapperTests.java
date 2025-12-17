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
package io.sapl.attributes;

import io.sapl.api.model.Value;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

class TimeOutWrapperTests {

    private static final Value TIME_OUT = Value.error("time out");
    private static final Value EMPTY    = Value.error("empty");

    @Test
    void when_emptyFlux_then_emptyValueEmitted() {
        final var source  = Flux.<Value>empty();
        final var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(2500L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).thenAwait(Duration.ofMillis(100L)).expectNext(EMPTY).verifyComplete();
    }

    @Test
    void when_firstElementsAfterTimeOut_then_sendTimeOutAndThenRestOfFlux() {
        var source  = Flux.<Value>just(Value.of(1), Value.of(2), Value.of(3)).delayElements(Duration.ofMillis(20L));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(TIME_OUT, Value.of(1), Value.of(2), Value.of(3)).verifyComplete();
    }

    @Test
    void when_emptyAfterTimeout_then_sendTimeOutAndEmpty() {
        var source  = Flux.<Value>just(Value.of(1)).delayElements(Duration.ofMillis(300L)).filter(v -> false);
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMillis(100L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(TIME_OUT, EMPTY).verifyComplete();
    }

    @Test
    void when_elementAndClosed_then_noDelayTillWrappedClosed() {
        var source  = Flux.<Value>just(Value.of(0));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMinutes(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectNext(Value.of(0)).expectComplete().verify(Duration.ofMillis(20L));
    }

    @Test
    void when_error_then_noDelayTillWrappedClosed() {
        var source  = Flux.<Value>error(new IllegalStateException("testing"));
        var wrapped = TimeOutWrapper.wrap(source, Duration.ofMinutes(1L), TIME_OUT, EMPTY);
        StepVerifier.create(wrapped).expectError().verify(Duration.ofMillis(20L));
    }

}
