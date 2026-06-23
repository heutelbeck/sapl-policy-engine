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

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
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

    private PrintWriter writer;
    private boolean     writerUnavailable;
    private boolean     closed;
    private boolean     completed;

    SseConnection(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
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
        synchronized (lock) {
            if (completed) {
                return;
            }
            completed = true;
            try {
                asyncContext.complete();
            } catch (IllegalStateException e) {
                log.debug("SSE async context already completed: {}", e.getMessage());
            }
        }
    }

    private PrintWriter writer() {
        if (writer == null && !writerUnavailable) {
            try {
                writer = ((HttpServletResponse) asyncContext.getResponse()).getWriter();
            } catch (IOException | IllegalStateException e) {
                writerUnavailable = true;
                log.debug("SSE writer unavailable: {}", e.getMessage());
            }
        }
        return writer;
    }
}
