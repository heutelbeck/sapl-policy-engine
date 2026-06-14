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

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator.AuthenticationResult;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProtobufRSocketAcceptor disposes connections at JWT expiry")
class ProtobufRSocketAcceptorExpiryTests {

    @Mock
    private BlockingPolicyDecisionPoint blockingPdp;

    @Mock
    private ReactivePolicyDecisionPoint pdp;

    private RSocket accept(Instant expiresAt) {
        RSocketConnectionAuthenticator authenticator = setup -> Mono
                .just(new AuthenticationResult("tenant", expiresAt));
        val                            acceptor      = new ProtobufRSocketAcceptor(blockingPdp, pdp, authenticator);
        val                            sendingSocket = mock(RSocket.class);
        acceptor.accept(mock(ConnectionSetupPayload.class), sendingSocket).block();
        return sendingSocket;
    }

    @Test
    @DisplayName("connection is disposed shortly after the token expiry passes")
    void whenTokenExpiresThenConnectionDisposed() {
        val sendingSocket = accept(Instant.now().plusMillis(200));
        verify(sendingSocket, timeout(3000)).dispose();
    }

    @Test
    @DisplayName("an already-expired token disposes the connection immediately")
    void whenTokenAlreadyExpiredThenConnectionDisposedImmediately() {
        val sendingSocket = accept(Instant.now().minusSeconds(1));
        verify(sendingSocket, timeout(3000)).dispose();
    }

    @Test
    @DisplayName("a non-expiring credential leaves the connection open")
    void whenNoExpiryThenConnectionNotDisposed() {
        val sendingSocket = accept(null);
        verify(sendingSocket, after(500).never()).dispose();
    }
}
