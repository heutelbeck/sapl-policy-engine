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
package io.sapl.node.rsocket.pdp;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;

/**
 * A server TLS context whose certificate can be replaced while the server
 * runs. Each new connection takes the context that is current when it is
 * accepted, open connections keep theirs. The contexts are built with the JDK
 * provider, whose handlers are made from the engine alone.
 */
final class ReloadableSslContext extends SslContext {

    private final AtomicReference<SslContext> current;

    ReloadableSslContext(SslContext initial) {
        current = new AtomicReference<>(initial);
    }

    /**
     * Serves new connections with another context.
     *
     * @param next the context of the renewed certificate
     */
    void update(SslContext next) {
        current.set(next);
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public List<String> cipherSuites() {
        return current.get().cipherSuites();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return current.get().applicationProtocolNegotiator();
    }

    @Override
    public SSLEngine newEngine(ByteBufAllocator allocator) {
        return current.get().newEngine(allocator);
    }

    @Override
    public SSLEngine newEngine(ByteBufAllocator allocator, String peerHost, int peerPort) {
        return current.get().newEngine(allocator, peerHost, peerPort);
    }

    @Override
    public SSLSessionContext sessionContext() {
        return current.get().sessionContext();
    }

}
