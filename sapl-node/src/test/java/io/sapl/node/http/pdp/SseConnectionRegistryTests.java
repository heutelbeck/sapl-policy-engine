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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

@DisplayName("SseConnectionRegistry")
class SseConnectionRegistryTests {

    private final AtomicBoolean competitorHoldsLock = new AtomicBoolean(false);

    @Test
    @DisplayName("the shutdown write is serialized through the same per-connection writer lock the pump and keep-alive use")
    void whenConnectionRegisteredThenShutdownWriteHoldsTheWriterLock() throws Exception {
        val registry   = new SseConnectionRegistry();
        val writerLock = new Object();

        val writeSawCompetitorHoldingLock = new AtomicBoolean(false);
        val recordingWriter               = new PrintWriter(new Writer() {
                                              @Override
                                              public void write(char[] cbuf, int off, int len) {
                                                  if (competitorHoldsLock.get()) {
                                                      writeSawCompetitorHoldingLock.set(true);
                                                  }
                                              }

                                              @Override
                                              public void flush() {
                                                  // no-op
                                              }

                                              @Override
                                              public void close() {
                                                  // no-op
                                              }
                                          });

        val response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(recordingWriter);
        val context = mock(AsyncContext.class);
        when(context.getResponse()).thenReturn(response);

        registry.register(context, writerLock);

        val lockAcquired = new CountDownLatch(1);
        val releaseLock  = new CountDownLatch(1);
        val competitor   = new Thread(() -> {
                             synchronized (writerLock) {
                                 competitorHoldsLock.set(true);
                                 lockAcquired.countDown();
                                 try {
                                     releaseLock.await();
                                 } catch (InterruptedException e) {
                                     Thread.currentThread().interrupt();
                                 }
                                 competitorHoldsLock.set(false);
                             }
                         });
        competitor.start();
        lockAcquired.await();

        val shutdown = new Thread(
                () -> registry.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class))));
        shutdown.start();

        // While the competitor holds the lock the shutdown write must block, not race
        // in.
        shutdown.join(300);
        assertThat(shutdown.isAlive()).isTrue();

        releaseLock.countDown();
        shutdown.join(TimeUnit.SECONDS.toMillis(5));
        competitor.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(writeSawCompetitorHoldingLock).isFalse();
        verify(context).complete();
    }

    @Test
    @DisplayName("a connection registered after shutdown has begun is immediately drained, not leaked")
    void whenConnectionRegisteredAfterShutdownBeganThenItIsImmediatelyDrained() throws Exception {
        val registry = new SseConnectionRegistry();

        // Shutdown begins with no connections yet open, so the terminal scan finds
        // nothing to drain.
        registry.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));

        val shutdownFrameWritten = new AtomicBoolean(false);
        val lateWriter           = new PrintWriter(new Writer() {
                                     @Override
                                     public void write(char[] cbuf, int off, int len) {
                                         shutdownFrameWritten.set(true);
                                     }

                                     @Override
                                     public void flush() {
                                         // no-op
                                     }

                                     @Override
                                     public void close() {
                                         // no-op
                                     }
                                 });

        val response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(lateWriter);
        val lateContext = mock(AsyncContext.class);
        when(lateContext.getResponse()).thenReturn(response);

        // A client connects after draining started. It must receive the shutdown
        // signal and be completed rather than linger as a leaked open connection.
        registry.register(lateContext, new Object());

        assertThat(shutdownFrameWritten).isTrue();
        verify(lateContext).complete();
    }
}
