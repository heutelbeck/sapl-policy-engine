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
package io.sapl.node.http.servlet;

import java.util.Set;
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
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private static final String SHUTDOWN_EVENT   = "event: shutdown\ndata: SAPL Node stopping\n\n";
    private static final String LOG_DRAINING     = "Draining {} active SSE connection(s)";
    private static final String LOG_DRAIN_FAILED = "Failed to drain SSE connection: {}";

    private final Set<AsyncContext> open = ConcurrentHashMap.newKeySet();

    void register(AsyncContext context) {
        open.add(context);
    }

    void unregister(AsyncContext context) {
        open.remove(context);
    }

    @EventListener
    void onContextClosed(ContextClosedEvent event) {
        if (open.isEmpty()) {
            return;
        }
        log.info(LOG_DRAINING, open.size());
        for (val context : open) {
            try {
                val response = (HttpServletResponse) context.getResponse();
                val writer   = response.getWriter();
                writer.write(SHUTDOWN_EVENT);
                writer.flush();
                context.complete();
            } catch (Exception e) {
                log.debug(LOG_DRAIN_FAILED, e.getMessage());
            }
        }
        open.clear();
    }

}
