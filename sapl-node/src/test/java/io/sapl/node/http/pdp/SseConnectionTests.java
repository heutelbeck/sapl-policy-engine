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
package io.sapl.node.http.pdp;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SseConnection")
@ExtendWith(MockitoExtension.class)
class SseConnectionTests {

    @Mock
    AsyncContext asyncContext;

    @Test
    @DisplayName("only one keep-alive may be in flight, so a stalled client cannot pile up dispatches")
    void whenKeepAliveInFlightThenFurtherReservationsAreRefusedUntilReleased() {
        val connection = new SseConnection(asyncContext);

        assertThat(connection.tryBeginKeepAlive()).isTrue();
        assertThat(connection.tryBeginKeepAlive()).isFalse();

        connection.endKeepAlive();

        assertThat(connection.tryBeginKeepAlive()).isTrue();
    }

    @Test
    @DisplayName("complete() does not block behind an in-flight write flush, so expiry teardown stays prompt")
    void whenWriteFlushStalledThenCompleteReturnsPromptly() throws Exception {
        val flushEntered = new CountDownLatch(1);
        val releaseFlush = new CountDownLatch(1);
        val response     = org.mockito.Mockito.mock(HttpServletResponse.class);
        val writer       = new PrintWriter(new Writer() {
                             @Override
                             public void write(char[] cbuf, int off, int len) {
                                 // no-op
                             }

                             @Override
                             public void flush() {
                                 flushEntered.countDown();
                                 try {
                                     releaseFlush.await();
                                 } catch (InterruptedException e) {
                                     Thread.currentThread().interrupt();
                                 }
                             }

                             @Override
                             public void close() {
                                 // no-op
                             }
                         });
        when(asyncContext.getResponse()).thenReturn(response);
        when(response.getWriter()).thenReturn(writer);
        val connection = new SseConnection(asyncContext);

        val writeThread = new Thread(() -> connection.write("data: x\n\n"));
        writeThread.setDaemon(true);
        writeThread.start();
        assertThat(flushEntered.await(5, TimeUnit.SECONDS)).isTrue();

        val completeReturned = new CountDownLatch(1);
        val completeThread   = new Thread(() -> {
                                 connection.complete();
                                 completeReturned.countDown();
                             });
        completeThread.setDaemon(true);
        completeThread.start();

        assertThat(completeReturned.await(2, TimeUnit.SECONDS)).isTrue();
        verify(asyncContext).complete();

        releaseFlush.countDown();
    }
}
