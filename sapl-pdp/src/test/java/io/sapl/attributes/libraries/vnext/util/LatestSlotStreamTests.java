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
package io.sapl.attributes.libraries.vnext.util;

import io.sapl.api.model.Poll;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("LatestSlotStream")
class LatestSlotStreamTests {

    @Test
    @DisplayName("delivers a single put to a non-blocked tryNext")
    void whenPutBeforeTryNextThenValueIsDelivered() {
        val stream = new LatestSlotStream<String>();

        stream.put("first");

        assertThat(stream.tryNext()).isEqualTo(Poll.value("first"));
        assertThat(stream.tryNext()).isEqualTo(Poll.empty());
    }

    @Test
    @DisplayName("drops intermediate values when producer outpaces consumer (latest-wins)")
    void whenMultiplePutsBeforeTryNextThenOnlyLatestIsObserved() {
        val stream = new LatestSlotStream<String>();

        stream.put("first");
        stream.put("second");
        stream.put("third");

        assertThat(stream.tryNext()).isEqualTo(Poll.value("third"));
        assertThat(stream.tryNext()).isEqualTo(Poll.empty());
    }

    @Test
    @DisplayName("awaitNext blocks until a value is put and then returns it")
    void whenAwaitNextBeforePutThenBlocksUntilPut() throws InterruptedException {
        val stream   = new LatestSlotStream<String>();
        val received = new AtomicReference<String>();
        val consumer = Thread.startVirtualThread(() -> {
                         try {
                             received.set(stream.awaitNext());
                         } catch (InterruptedException ie) {
                             Thread.currentThread().interrupt();
                         }
                     });

        Thread.sleep(20L);
        assertThat(received.get()).isNull();
        stream.put("delivered");
        consumer.join(500L);

        assertThat(received.get()).isEqualTo("delivered");
    }

    @Test
    @DisplayName("awaitNext returns null after complete with empty slot")
    void whenCompleteWithoutPendingValueThenAwaitNextReturnsNull() throws InterruptedException {
        val stream = new LatestSlotStream<String>();

        stream.complete();

        assertThat(stream.awaitNext()).isNull();
    }

    @Test
    @DisplayName("tryNext returns Poll.done after complete with empty slot")
    void whenCompleteWithoutPendingValueThenTryNextIsDone() {
        val stream = new LatestSlotStream<String>();

        stream.complete();

        assertThat(stream.tryNext()).isEqualTo(Poll.done());
    }

    @Test
    @DisplayName("put after complete is a no-op")
    void whenPutAfterCompleteThenValueIsNotDelivered() {
        val stream = new LatestSlotStream<String>();

        stream.complete();
        stream.put("ignored");

        assertThat(stream.tryNext()).isEqualTo(Poll.done());
    }

    @Test
    @DisplayName("close completes the stream and runs the close action exactly once")
    void whenCloseThenCloseActionRunsExactlyOnce() {
        val stream    = new LatestSlotStream<String>();
        val callCount = new AtomicInteger();
        stream.onClose(callCount::incrementAndGet);

        stream.close();
        stream.close();

        assertThat(callCount).hasValue(1);
        assertThat(stream.tryNext()).isEqualTo(Poll.done());
    }

    @Test
    @DisplayName("awaitNext returns the pending value before signalling completion")
    void whenCompleteAfterPutThenAwaitNextDeliversBeforeCompletion() throws InterruptedException {
        val stream = new LatestSlotStream<String>();

        stream.put("before-complete");
        stream.complete();

        assertThat(stream.awaitNext()).isEqualTo("before-complete");
        assertThat(stream.awaitNext()).isNull();
    }

    @Test
    @DisplayName("blocked awaitNext is unblocked by complete")
    void whenAwaitNextThenCompleteThenAwaitNextReturnsNull() {
        val stream = new LatestSlotStream<String>();
        val woke   = new AtomicBoolean(false);
        val thread = Thread.startVirtualThread(() -> {
                       try {
                           stream.awaitNext();
                           woke.set(true);
                       } catch (InterruptedException ie) {
                           Thread.currentThread().interrupt();
                       }
                   });

        await().atMost(Duration.ofSeconds(1)).until(() -> thread.getState() == Thread.State.WAITING);
        stream.complete();

        await().atMost(Duration.ofSeconds(1)).untilTrue(woke);
    }
}
