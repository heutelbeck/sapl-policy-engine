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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@DisplayName("LSP WebSocket endpoint origin policy")
class LspWebSocketConfigurationTests {

    @Test
    @DisplayName("does not register a wildcard origin by default")
    void whenDefaultConfigurationThenNoWildcardOrigin() {
        final var registry = registerHandlers(new LspWebSocketProperties());

        assertThat(registry.allowedOrigins).doesNotContain("*");
        assertThat(registry.allowedOriginPatterns).doesNotContain("*");
    }

    @Test
    @DisplayName("registers the endpoint at /sapl-lsp")
    void whenConfiguredThenEndpointRegisteredAtSaplLsp() {
        final var registry = registerHandlers(new LspWebSocketProperties());

        assertThat(registry.paths).containsExactly("/sapl-lsp");
    }

    @Test
    @DisplayName("applies the configured allow-list as origin patterns")
    void whenOriginsConfiguredThenAppliedAsOriginPatterns() {
        final var properties = new LspWebSocketProperties();
        properties.setAllowedOrigins(List.of("https://editor.example.org"));

        final var registry = registerHandlers(properties);

        assertThat(registry.allowedOriginPatterns).containsExactly("https://editor.example.org");
    }

    private static RecordingRegistry registerHandlers(LspWebSocketProperties properties) {
        final var configuration = new LspWebSocketConfiguration(properties);
        final var registry      = new RecordingRegistry();
        configuration.registerWebSocketHandlers(registry);
        return registry;
    }

    private static final class RecordingRegistry implements WebSocketHandlerRegistry, WebSocketHandlerRegistration {

        private final List<String> paths                 = new ArrayList<>();
        private final List<String> allowedOrigins        = new ArrayList<>();
        private final List<String> allowedOriginPatterns = new ArrayList<>();

        @Override
        public WebSocketHandlerRegistration addHandler(WebSocketHandler handler, String... handlerPaths) {
            paths.addAll(Arrays.asList(handlerPaths));
            return this;
        }

        @Override
        public WebSocketHandlerRegistration setHandshakeHandler(HandshakeHandler handshakeHandler) {
            return this;
        }

        @Override
        public WebSocketHandlerRegistration addInterceptors(HandshakeInterceptor... interceptors) {
            return this;
        }

        @Override
        public WebSocketHandlerRegistration setAllowedOrigins(String... origins) {
            allowedOrigins.addAll(Arrays.asList(origins));
            return this;
        }

        @Override
        public WebSocketHandlerRegistration setAllowedOriginPatterns(String... patterns) {
            allowedOriginPatterns.addAll(Arrays.asList(patterns));
            return this;
        }

        @Override
        public SockJsServiceRegistration withSockJS() {
            throw new UnsupportedOperationException("SockJS registration is not exercised by these tests");
        }
    }
}
