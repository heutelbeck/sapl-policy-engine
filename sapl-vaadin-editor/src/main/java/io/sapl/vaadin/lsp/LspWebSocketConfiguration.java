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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring configuration for the SAPL LSP WebSocket endpoint.
 * Registers the WebSocket handler at /sapl-lsp for LSP communication.
 *
 * <p>
 * Cross-origin access is closed by default (same-origin only). Operators that
 * front the editor from a different origin opt in explicitly through
 * {@code sapl.editor.lsp.allowed-origins}. No wildcard origin is ever shipped.
 * Authentication is left to the consuming application, which can authorize the
 * {@code /sapl-lsp} handshake through its own Spring Security filter chain.
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(LspWebSocketProperties.class)
public class LspWebSocketConfiguration implements WebSocketConfigurer {

    private final LspWebSocketProperties properties;

    public LspWebSocketConfiguration(LspWebSocketProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LspWebSocketEndpoint lspWebSocketEndpoint() {
        return new LspWebSocketEndpoint(properties);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        final WebSocketHandlerRegistration registration   = registry.addHandler(lspWebSocketEndpoint(), "/sapl-lsp");
        final var                          allowedOrigins = properties.getAllowedOrigins();
        if (!allowedOrigins.isEmpty()) {
            registration.setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        }
    }
}
