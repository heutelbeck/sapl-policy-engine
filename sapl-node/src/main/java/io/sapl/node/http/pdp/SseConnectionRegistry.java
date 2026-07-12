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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Tracks open SSE connections so that on Spring context close each connection
 * receives a final shutdown event before the pump executor is interrupted.
 * Without this hook clients see an abrupt TCP close and have no signal to stop
 * reconnecting.
 * <p>
 * The shutdown drain and the pump teardown both finish a connection through the
 * same {@link SseConnection}, which serializes the writes on its own monitor so
 * the final frame cannot interleave with a concurrent pump or keep-alive write,
 * and completes the async context at most once.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private static final String SHUTDOWN_EVENT   = "event: shutdown\ndata: SAPL Node stopping\n\n";
    private static final String LOG_DRAINING     = "Draining {} active SSE connection(s)";
    private static final String LOG_DRAIN_FAILED = "Failed to drain SSE connection: {}";

    private final Set<SseConnection> open = ConcurrentHashMap.newKeySet();

    private volatile boolean shuttingDown = false;

    void register(SseConnection connection) {
        if (shuttingDown) {
            drain(connection);
            return;
        }
        open.add(connection);
        // Shutdown may have begun between the flag check and the add. Drain here so
        // the connection cannot leak past a terminal scan that already happened.
        if (shuttingDown && open.remove(connection)) {
            drain(connection);
        }
    }

    void unregister(SseConnection connection) {
        open.remove(connection);
    }

    @EventListener
    void onContextClosed(ContextClosedEvent event) {
        shuttingDown = true;
        if (open.isEmpty()) {
            return;
        }
        log.info(LOG_DRAINING, open.size());
        for (val connection : open) {
            if (open.remove(connection)) {
                drain(connection);
            }
        }
    }

    private void drain(SseConnection connection) {
        try {
            connection.writeQuietly(SHUTDOWN_EVENT);
            connection.complete();
        } catch (Exception e) {
            log.debug(LOG_DRAIN_FAILED, e.getMessage());
        }
    }

}
