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
package io.sapl.pdp.plugins;

import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StaticPluginsSource")
class StaticPluginsSourceTests {

    private static PluginsBundle bundle() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    @Test
    @DisplayName("a subscriber receives the bundle on subscribe")
    void whenSubscribeThenReceivesBundle() {
        val source   = new StaticPluginsSource(bundle());
        val received = new AtomicReference<PluginsBundle>();

        source.subscribe(received::set);

        assertThat(received.get()).isNotNull();
        assertThat(source.registeredListenerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe after close is a no-op and registers nothing")
    void whenClosedThenSubscribeIsNoOp() {
        val source   = new StaticPluginsSource(bundle());
        val received = new AtomicReference<PluginsBundle>();
        source.close();

        source.subscribe(received::set);

        assertThat(received.get()).isNull();
        assertThat(source.registeredListenerCount()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("subscribe racing close never leaves a listener registered after the source is closed")
    void whenSubscribeRacesCloseThenNoListenerRetained() throws InterruptedException {
        val bundle = bundle();
        var leaked = 0;

        for (int i = 0; i < 20000; i++) {
            val                     source     = new StaticPluginsSource(bundle);
            val                     start      = new CountDownLatch(1);
            Consumer<PluginsBundle> listener   = b -> {};
            val                     subscriber = Thread.ofVirtual().unstarted(() -> {
                                                   awaitQuietly(start);
                                                   source.subscribe(listener);
                                               });
            val                     closer     = Thread.ofVirtual().unstarted(() -> {
                                                   awaitQuietly(start);
                                                   source.close();
                                               });
            subscriber.start();
            closer.start();
            start.countDown();
            subscriber.join();
            closer.join();

            if (source.registeredListenerCount() != 0) {
                leaked++;
            }
        }

        assertThat(leaked).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
