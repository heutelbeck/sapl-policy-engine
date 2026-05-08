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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueueStream")
class QueueStreamTests {

    @Test
    @DisplayName("delivers every value in FIFO order, dropping none")
    void whenMultiplePutsThenAllDeliveredInOrder() throws InterruptedException {
        val stream = new QueueStream<String>();

        stream.put("first");
        stream.put("second");
        stream.put("third");

        assertThat(stream.awaitNext()).isEqualTo("first");
        assertThat(stream.awaitNext()).isEqualTo("second");
        assertThat(stream.awaitNext()).isEqualTo("third");
    }

    @Test
    @DisplayName("tryNext returns Poll.empty when queue is empty but not completed")
    void whenQueueEmptyAndNotCompletedThenTryNextEmpty() {
        val stream = new QueueStream<String>();

        assertThat(stream.tryNext()).isEqualTo(Poll.empty());
    }

    @Test
    @DisplayName("awaitNext returns null after queued values are drained and completed")
    void whenCompletedAfterDrainThenAwaitNextReturnsNull() throws InterruptedException {
        val stream = new QueueStream<String>();
        stream.put("only");
        stream.complete();

        assertThat(stream.awaitNext()).isEqualTo("only");
        assertThat(stream.awaitNext()).isNull();
    }

    @Test
    @DisplayName("tryNext returns Poll.done after completion is reached")
    void whenCompletionDrainedThenTryNextStaysDone() throws InterruptedException {
        val stream = new QueueStream<String>();
        stream.put("one");
        stream.complete();

        assertThat(stream.awaitNext()).isEqualTo("one");
        assertThat(stream.tryNext()).isEqualTo(Poll.done());
        assertThat(stream.tryNext()).isEqualTo(Poll.done());
    }

    @Test
    @DisplayName("put after complete is a no-op")
    void whenPutAfterCompleteThenIgnored() throws InterruptedException {
        val stream = new QueueStream<String>();
        stream.put("first");
        stream.complete();
        stream.put("ignored");

        assertThat(stream.awaitNext()).isEqualTo("first");
        assertThat(stream.awaitNext()).isNull();
    }

    @Test
    @DisplayName("close completes the stream and runs the close action exactly once")
    void whenCloseTwiceThenCloseActionRunsOnce() {
        val stream    = new QueueStream<String>();
        val callCount = new AtomicInteger();
        stream.onClose(callCount::incrementAndGet);

        stream.close();
        stream.close();

        assertThat(callCount).hasValue(1);
    }
}
