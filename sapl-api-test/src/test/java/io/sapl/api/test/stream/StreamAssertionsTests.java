/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.test.stream;

import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Streams;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StreamAssertions")
class StreamAssertionsTests {

    @Test
    @DisplayName("awaitsNext passes when the next emitted value equals expected")
    void whenNextValueMatchesThenPasses() {
        val stream = Streams.just(Value.of("hello"));

        StreamAssertions.assertThat(stream).awaitsNext(Value.of("hello"));
    }

    @Test
    @DisplayName("awaitsNext fails when the next emitted value differs from expected")
    void whenNextValueMismatchesThenFails() {
        val stream = Streams.just(Value.of("actual"));

        assertThatThrownBy(() -> StreamAssertions.assertThat(stream).awaitsNext(Value.of("expected")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("expected").hasMessageContaining("actual");
    }

    @Test
    @DisplayName("awaitsNext fails when the stream times out before producing a value")
    void whenNoValueWithinTimeoutThenFails() {
        val stream = new LatestSlotStream<Value>();

        assertThatThrownBy(() -> StreamAssertions.assertThat(stream).withinTimeout(Duration.ofMillis(50))
                .awaitsNext(Value.of("expected"))).isInstanceOf(AssertionError.class).hasMessageContaining("Timed out");

        stream.close();
    }

    @Test
    @DisplayName("awaitsNext with consumer passes when the requirements hold")
    void whenConsumerRequirementsHoldThenPasses() {
        val stream = Streams.just(Value.of("hello"));

        StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isEqualTo(Value.of("hello")));
    }

    @Test
    @DisplayName("awaitsCompletion passes when the stream completes within timeout")
    void whenStreamCompletesThenAwaitsCompletionPasses() {
        val stream = Streams.empty();

        StreamAssertions.assertThat(stream).awaitsCompletion();
    }

    @Test
    @DisplayName("awaitsCompletion fails when the stream emits a further value")
    void whenStreamEmitsThenAwaitsCompletionFails() {
        val stream = Streams.just(Value.of("unexpected"));

        assertThatThrownBy(() -> StreamAssertions.assertThat(stream).awaitsCompletion())
                .isInstanceOf(AssertionError.class).hasMessageContaining("unexpected");
    }

    @Test
    @DisplayName("awaitsCompletion fails when the stream neither completes nor emits within timeout")
    void whenStreamHangsThenAwaitsCompletionTimesOut() {
        val stream = new LatestSlotStream<Value>();

        assertThatThrownBy(
                () -> StreamAssertions.assertThat(stream).withinTimeout(Duration.ofMillis(50)).awaitsCompletion())
                .isInstanceOf(AssertionError.class).hasMessageContaining("Timed out");

        stream.close();
    }

    @Test
    @DisplayName("drain collects every value emitted before completion")
    void whenDrainOverFiniteSourceThenAllValuesCollected() {
        val stream = Streams.just(Value.of("only"));

        val collected = StreamAssertions.assertThat(stream).withinTimeout(Duration.ofMillis(200)).drain();

        assertThat(collected).containsExactly(Value.of("only"));
    }
}
