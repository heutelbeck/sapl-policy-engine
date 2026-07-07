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
package io.sapl.node.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.val;

@DisplayName("RateLimit")
class RateLimitTests {

    @Test
    @DisplayName("a full second's worth of permits is available as an initial burst, then the rate is enforced")
    void whenBurstExhaustedThenFurtherRequestsShed() {
        val clock = new AtomicLong(1_000_000L);
        val limit = new RateLimit(5, clock::get);
        for (int i = 0; i < 5; i++) {
            assertThat(limit.tryAcquire()).as("burst permit %d", i).isTrue();
        }
        assertThat(limit.tryAcquire()).as("over-burst request").isFalse();
    }

    @Test
    @DisplayName("permits refill at the configured steady rate as time advances")
    void whenTimeAdvancesThenPermitsRefill() {
        val clock = new AtomicLong(1_000_000L);
        val limit = new RateLimit(5, clock::get);
        for (int i = 0; i < 5; i++) {
            assertThat(limit.tryAcquire()).isTrue();
        }
        assertThat(limit.tryAcquire()).isFalse();
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(200));
        assertThat(limit.tryAcquire()).as("one emission interval later").isTrue();
        assertThat(limit.tryAcquire()).as("still within the same interval").isFalse();
    }

    @Test
    @DisplayName("a long idle period restores at most the burst capacity")
    void whenLongIdleThenBurstIsCappedAtOneSecond() {
        val clock = new AtomicLong(1_000_000L);
        val limit = new RateLimit(2, clock::get);
        assertThat(limit.tryAcquire()).isTrue();
        assertThat(limit.tryAcquire()).isTrue();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        assertThat(limit.tryAcquire()).isTrue();
        assertThat(limit.tryAcquire()).isTrue();
        assertThat(limit.tryAcquire()).as("beyond the restored burst").isFalse();
    }

    @ValueSource(ints = { 0, -1 })
    @ParameterizedTest(name = "rate {0} is rejected")
    @DisplayName("non-positive rates are rejected at construction")
    void whenRateNotPositiveThenConstructionFails(int rate) {
        assertThatThrownBy(() -> new RateLimit(rate)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
