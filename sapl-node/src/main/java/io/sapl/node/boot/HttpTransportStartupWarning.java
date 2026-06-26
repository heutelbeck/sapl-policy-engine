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
package io.sapl.node.boot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Emits a factual WARN when the HTTP server binds without TLS so operators
 * see the cleartext-transport implication in the boot log without having to
 * cross-reference the configuration file.
 */
@Slf4j
@Component
class HttpTransportStartupWarning {

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @EventListener
    void onWebServerInitialized(WebServerInitializedEvent event) {
        if (sslEnabled) {
            return;
        }
        log.warn("HTTP server bound on port {} without TLS. Credentials (basic auth, API key, JWT) and "
                + "authorization decisions traverse the network in cleartext. Configure server.ssl.bundle, "
                + "or terminate TLS at an upstream load balancer.", event.getWebServer().getPort());
    }

}
