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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.sapl.node.SaplNodeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Emits a factual WARN when a transport binds to a non-loopback address while
 * anonymous access is permitted ({@code allow-no-auth}). Running without
 * authentication is a legitimate choice when an outer layer owns the trust
 * boundary (a service mesh, a private network segment, or an authenticating
 * reverse proxy), so this is a visible reminder rather than a refusal to
 * start. On loopback, or when an authentication mode gates access, the warning
 * stays silent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AnonymousAccessStartupWarning {

    private static final String TRANSPORT_HTTP    = "HTTP server";
    private static final String TRANSPORT_RSOCKET = "RSocket server";

    private static final String WARN_ANONYMOUS_NETWORK_EXPOSURE = "{} is bound to a non-loopback address ({}) while "
            + "anonymous access is enabled (allow-no-auth). Unauthenticated clients that can reach this address may "
            + "request authorization decisions. Enable an authentication mode, or restrict the bind address to "
            + "loopback if this exposure is unintended.";

    private final SaplNodeProperties properties;

    @Value("${server.address:0.0.0.0}")
    private String httpAddress;

    @Value("${sapl.pdp.rsocket.enabled:true}")
    private boolean rsocketEnabled;

    @Value("${sapl.pdp.rsocket.address:127.0.0.1}")
    private String rsocketAddress;

    @Value("${sapl.pdp.rsocket.socket-path:#{null}}")
    private String rsocketSocketPath;

    @EventListener
    void onReady(ApplicationReadyEvent event) {
        warnIfAnonymousAndNetworkExposed();
    }

    void warnIfAnonymousAndNetworkExposed() {
        if (!properties.isAllowNoAuth()) {
            return;
        }
        if (isNetworkExposed(httpAddress)) {
            log.warn(WARN_ANONYMOUS_NETWORK_EXPOSURE, TRANSPORT_HTTP, httpAddress);
        }
        if (rsocketEnabled && !isUnixSocket() && isNetworkExposed(rsocketAddress)) {
            log.warn(WARN_ANONYMOUS_NETWORK_EXPOSURE, TRANSPORT_RSOCKET, rsocketAddress);
        }
    }

    private boolean isUnixSocket() {
        return rsocketSocketPath != null && !rsocketSocketPath.isBlank();
    }

    private static boolean isNetworkExposed(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            return !InetAddress.getByName(address.trim()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            // Treat an unresolvable address as exposed rather than silently ignore a
            // misconfiguration.
            return true;
        }
    }

}
