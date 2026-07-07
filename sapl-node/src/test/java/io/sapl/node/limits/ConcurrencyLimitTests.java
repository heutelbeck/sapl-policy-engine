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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.val;

@DisplayName("ConcurrencyLimit")
class ConcurrencyLimitTests {

    @Test
    @DisplayName("permits are granted up to the ceiling and refused beyond it")
    void whenCeilingReachedThenAcquireRefused() {
        val limit  = new ConcurrencyLimit(2);
        val first  = limit.tryAcquire();
        val second = limit.tryAcquire();
        val third  = limit.tryAcquire();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(third).isNull();
        assertThat(limit.active()).isEqualTo(2);
    }

    @Test
    @DisplayName("releasing a permit frees a slot for a new acquisition")
    void whenPermitReleasedThenSlotReusable() {
        val limit = new ConcurrencyLimit(1);
        val held  = limit.tryAcquire();
        assertThat(held).isNotNull();
        assertThat(limit.tryAcquire()).isNull();
        held.close();
        assertThat(limit.active()).isZero();
        assertThat(limit.tryAcquire()).isNotNull();
    }

    @Test
    @DisplayName("closing a permit twice releases the slot only once")
    void whenPermitClosedTwiceThenSlotFreedOnce() {
        val limit = new ConcurrencyLimit(2);
        val held  = limit.tryAcquire();
        assertThat(held).isNotNull();
        assertThat(limit.tryAcquire()).isNotNull();
        held.close();
        held.close();
        assertThat(limit.active()).isEqualTo(1);
    }

    @ValueSource(ints = { 0, -1 })
    @ParameterizedTest(name = "ceiling {0} is rejected")
    @DisplayName("non-positive ceilings are rejected at construction")
    void whenCeilingNotPositiveThenConstructionFails(int ceiling) {
        assertThatThrownBy(() -> new ConcurrencyLimit(ceiling)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @Timeout(10)
    @DisplayName("the ceiling holds under concurrent acquisition, no oversubscription and no lost slots")
    void whenAcquiredConcurrentlyThenCeilingHolds() throws InterruptedException {
        val ceiling     = 8;
        val threadCount = 64;
        val limit       = new ConcurrencyLimit(ceiling);
        val startGate   = new CountDownLatch(1);
        val done        = new CountDownLatch(threadCount);
        val granted     = new AtomicInteger();
        try (val executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    try {
                        startGate.await();
                        val permit = limit.tryAcquire();
                        if (permit != null) {
                            granted.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(granted.get()).isEqualTo(ceiling);
        assertThat(limit.active()).isEqualTo(ceiling);
    }
}
