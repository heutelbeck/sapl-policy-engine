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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import jakarta.servlet.AsyncContext;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Owns one SSE connection's response writer, the monitor that serialises writes
 * to it, and its lifecycle flags. The pump, the keep-alive task, and the
 * registry's shutdown drain all write through this type, so writes to the
 * non-thread-safe {@link PrintWriter} serialise on a monitor this object owns,
 * and the {@link AsyncContext} completes at most once regardless of which path
 * finishes the connection first.
 */
@Slf4j
final class SseConnection {

    private final AsyncContext asyncContext;
    private final Object       lock = new Object();

    private final AtomicBoolean keepAliveInFlight = new AtomicBoolean();

    // Lifecycle state stays off the write monitor so teardown never blocks behind a
    // stalled flush.
    private final AtomicBoolean                       completed   = new AtomicBoolean();
    private final AtomicReference<ScheduledFuture<?>> expiryTask  = new AtomicReference<>();
    private final AtomicReference<AutoCloseable>      boundStream = new AtomicReference<>();

    private PrintWriter writer;
    private boolean     writerUnavailable;
    private boolean     closed;

    SseConnection(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
    }

    /**
     * Reserves the single keep-alive slot for this connection. Returns false when
     * a prior keep-alive is still in flight, so a stalled client cannot make ticks
     * pile up parked virtual threads on the write monitor.
     *
     * @return true if the slot was reserved, false if one is already in flight
     */
    boolean tryBeginKeepAlive() {
        return keepAliveInFlight.compareAndSet(false, true);
    }

    /**
     * Releases the keep-alive slot reserved by {@link #tryBeginKeepAlive()}.
     */
    void endKeepAlive() {
        keepAliveInFlight.set(false);
    }

    /**
     * Writes one frame and flushes. Returns true while the connection is still
     * usable, false once it is closed or the client has disconnected.
     *
     * @param frame the SSE frame to write
     * @return true if the connection is still healthy, false otherwise
     */
    boolean write(String frame) {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            val target = writer();
            if (target == null) {
                return false;
            }
            target.write(frame);
            target.flush();
            return !target.checkError();
        }
    }

    /**
     * Writes a best-effort frame, ignoring a disconnected client. No-op once the
     * connection is closed.
     *
     * @param frame the SSE frame to write
     */
    void writeQuietly(String frame) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            val target = writer();
            if (target == null) {
                return;
            }
            target.write(frame);
            target.flush();
        }
    }

    /**
     * Marks the connection closed and closes the writer. Subsequent writes are
     * skipped.
     */
    void close() {
        synchronized (lock) {
            closed = true;
            // Obtain the writer if no frame was ever written, so the response writer
            // is still closed on teardown.
            val target = writer();
            if (target != null) {
                target.close();
            }
        }
    }

    /**
     * Completes the async context at most once, whichever of the pump teardown or
     * the shutdown drain reaches it first.
     */
    void complete() {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        val task = expiryTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
        try {
            asyncContext.complete();
        } catch (IllegalStateException e) {
            log.debug("SSE async context already completed: {}", e.getMessage());
        }
    }

    /**
     * Binds the token-expiry timer so it is cancelled when the connection
     * completes, releasing the task and the connection reference it captures. If
     * the connection has already completed, the task is cancelled immediately.
     *
     * @param task the scheduled expiry task
     */
    void setExpiryTask(ScheduledFuture<?> task) {
        expiryTask.set(task);
        // Cancel the task if the connection completed between scheduling and binding.
        if (completed.get() && expiryTask.compareAndSet(task, null)) {
            task.cancel(false);
        }
    }

    /**
     * Binds the decision stream so the expiry timer can close it, releasing the
     * upstream PDP subscription promptly when the credential expires. If the
     * connection has already completed, the stream is closed immediately.
     *
     * @param stream the decision stream feeding this connection
     */
    void bindStream(AutoCloseable stream) {
        boundStream.set(stream);
        // Close immediately if the connection already completed before binding.
        if (completed.get() && boundStream.compareAndSet(stream, null)) {
            closeQuietly(stream);
        }
    }

    /**
     * Closes the bound decision stream, waking the parked pump so it tears down.
     * Idempotent and safe to call from the expiry timer thread. The stream is
     * closed outside the monitor so its close action never runs under this lock.
     */
    void closeStream() {
        closeQuietly(boundStream.getAndSet(null));
    }

    private static void closeQuietly(@Nullable AutoCloseable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception e) {
            log.debug("SSE stream close failed: {}", e.getMessage());
        }
    }

    private PrintWriter writer() {
        if (writer == null && !writerUnavailable) {
            try {
                writer = asyncContext.getResponse().getWriter();
            } catch (IOException | IllegalStateException e) {
                writerUnavailable = true;
                log.debug("SSE writer unavailable: {}", e.getMessage());
            }
        }
        return writer;
    }
}
