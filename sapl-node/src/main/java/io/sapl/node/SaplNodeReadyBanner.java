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
package io.sapl.node;

import java.time.Duration;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Prints a ready summary at boot so operators see endpoints, ports, and
 * active auth methods at a glance instead of greping the boot log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SaplNodeReadyBanner {

    private static final String AUTH_NONE      = "no-auth";
    private static final String AUTH_BASIC     = "basic-auth";
    private static final String AUTH_API_KEY   = "api-key";
    private static final String AUTH_OAUTH2    = "oauth2";
    private static final String AUTH_SEPARATOR = ", ";
    private static final String DISABLED       = "disabled";

    private final SaplNodeProperties properties;

    @Value("${server.ssl.enabled:false}")
    private boolean httpSslEnabled;

    @Value("${sapl.pdp.rsocket.enabled:true}")
    private boolean rsocketEnabled;

    @Value("${sapl.pdp.rsocket.port:7000}")
    private int rsocketPort;

    @Value("${sapl.pdp.rsocket.socket-path:#{null}}")
    private String rsocketSocketPath;

    @Value("${sapl.pdp.rsocket.ssl.bundle:#{null}}")
    private String rsocketSslBundle;

    private int  httpPort     = -1;
    private long readyAtNanos = 0L;

    @EventListener
    void onWebServerInitialized(WebServerInitializedEvent event) {
        httpPort = event.getWebServer().getPort();
    }

    @EventListener
    void onReady(ApplicationReadyEvent event) {
        readyAtNanos = System.nanoTime();
        log.info("SAPL Node ready");
        log.info("HTTP    {}", httpEndpoint());
        log.info("RSocket {}", rsocketEndpoint());
        log.info("Auth    {}", authMethods());
    }

    @EventListener
    void onStopping(ContextClosedEvent event) {
        if (readyAtNanos == 0L) {
            log.info("SAPL Node stopping");
            return;
        }
        val uptime = Duration.ofNanos(System.nanoTime() - readyAtNanos);
        log.info("SAPL Node stopping after {}", formatUptime(uptime));
    }

    private static String formatUptime(Duration uptime) {
        val seconds = uptime.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        val minutes = uptime.toMinutes();
        if (minutes < 60) {
            return "%dm %ds".formatted(minutes, seconds % 60);
        }
        val hours = uptime.toHours();
        return "%dh %dm".formatted(hours, minutes % 60);
    }

    private String httpEndpoint() {
        val scheme = httpSslEnabled ? "https" : "http";
        return "%s://localhost:%d  (Scalar UI: %s://localhost:%d/scalar)".formatted(scheme, httpPort, scheme, httpPort);
    }

    private String rsocketEndpoint() {
        if (!rsocketEnabled) {
            return DISABLED;
        }
        val tls = rsocketSslBundle != null && !rsocketSslBundle.isBlank() ? "tls" : "tcp";
        if (rsocketSocketPath != null && !rsocketSocketPath.isBlank()) {
            return "unix:%s  (%s)".formatted(rsocketSocketPath, tls);
        }
        return "%s://localhost:%d".formatted(tls, rsocketPort);
    }

    private String authMethods() {
        val active = new ArrayList<String>();
        if (properties.isAllowNoAuth()) {
            active.add(AUTH_NONE);
        }
        if (properties.isAllowBasicAuth()) {
            active.add(AUTH_BASIC);
        }
        if (properties.isAllowApiKeyAuth()) {
            active.add(AUTH_API_KEY);
        }
        if (properties.isAllowOauth2Auth()) {
            active.add(AUTH_OAUTH2);
        }
        return active.isEmpty() ? DISABLED : String.join(AUTH_SEPARATOR, active);
    }

}
