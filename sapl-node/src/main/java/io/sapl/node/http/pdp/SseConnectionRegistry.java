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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Tracks open SSE AsyncContexts so that on Spring context close each
 * connection receives a final shutdown event before the pump executor is
 * interrupted. Without this hook clients see an abrupt TCP close and have
 * no signal to stop reconnecting.
 * <p>
 * Each connection is registered together with its per-connection writer lock,
 * the same lock the pump and keep-alive writes synchronize on. The shutdown
 * write acquires that lock so the final frame cannot interleave with a
 * concurrent pump or keep-alive write to the same non-thread-safe
 * {@code PrintWriter}.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private static final String SHUTDOWN_EVENT   = "event: shutdown\ndata: SAPL Node stopping\n\n";
    private static final String LOG_DRAINING     = "Draining {} active SSE connection(s)";
    private static final String LOG_DRAIN_FAILED = "Failed to drain SSE connection: {}";

    private final Map<AsyncContext, Object> open = new ConcurrentHashMap<>();

    private volatile boolean shuttingDown = false;

    void register(AsyncContext context, Object writerLock) {
        if (shuttingDown) {
            drain(context, writerLock);
            return;
        }
        open.put(context, writerLock);
        if (shuttingDown) {
            // Shutdown began between the flag check and the put. Drain here so the
            // connection cannot leak past a terminal scan that already happened.
            if (open.remove(context) != null) {
                drain(context, writerLock);
            }
        }
    }

    void unregister(AsyncContext context) {
        open.remove(context);
    }

    @EventListener
    void onContextClosed(ContextClosedEvent event) {
        shuttingDown = true;
        if (open.isEmpty()) {
            return;
        }
        log.info(LOG_DRAINING, open.size());
        for (val entry : open.entrySet()) {
            if (open.remove(entry.getKey()) != null) {
                drain(entry.getKey(), entry.getValue());
            }
        }
    }

    private void drain(AsyncContext context, Object writerLock) {
        try {
            val response = (HttpServletResponse) context.getResponse();
            val writer   = response.getWriter();
            synchronized (writerLock) {
                writer.write(SHUTDOWN_EVENT);
                writer.flush();
            }
            context.complete();
        } catch (Exception e) {
            log.debug(LOG_DRAIN_FAILED, e.getMessage());
        }
    }

}
