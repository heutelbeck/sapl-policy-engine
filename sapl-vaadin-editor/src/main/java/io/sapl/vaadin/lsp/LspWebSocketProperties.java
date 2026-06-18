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
package io.sapl.vaadin.lsp;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration for the embedded SAPL Language Server WebSocket endpoint.
 *
 * <p>
 * The endpoint is secured by sensible defaults. Cross-origin access is closed
 * (same-origin only) and the resource footprint is bounded so that a single
 * deployment cannot be exhausted by many or stalled editor sessions.
 * Authentication is intentionally left to the consuming application, which can
 * authorize the {@code /sapl-lsp} handshake through its own Spring Security
 * filter chain. A public, anonymous editor (such as the playground) keeps
 * working same-origin without any further configuration.
 */
@Data
@ConfigurationProperties(prefix = "sapl.editor.lsp")
public class LspWebSocketProperties {

    /**
     * Origins allowed to open cross-origin connections to {@code /sapl-lsp},
     * bound via {@code setAllowedOriginPatterns}. Empty by default, which keeps
     * Spring's same-origin policy and never ships a wildcard.
     */
    private List<String> allowedOrigins = List.of();

    /**
     * Maximum number of concurrent LSP sessions. Connections beyond this cap are
     * closed with {@code SERVICE_OVERLOAD} so a flood cannot allocate unbounded
     * threads and language-server instances.
     */
    private int maxConcurrentSessions = 50;

    /**
     * Capacity of the per-session queue buffering client messages handed to the
     * embedded LSP server. Bounds memory per session.
     */
    private int messageQueueCapacity = 256;

    /**
     * Time in milliseconds the container thread waits to enqueue a client message
     * before giving up and closing the session. Prevents a stalled LSP server
     * from blocking the container thread.
     */
    private long offerTimeoutMillis = 2_000;
}
