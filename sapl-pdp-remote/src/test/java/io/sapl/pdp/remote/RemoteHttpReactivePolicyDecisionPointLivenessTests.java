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
package io.sapl.pdp.remote;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("RemoteHttpReactivePolicyDecisionPoint liveness")
class RemoteHttpReactivePolicyDecisionPointLivenessTests {

    private static final String     SUBJECT  = "willi";
    private static final String     ACTION   = "read";
    private static final String     RESOURCE = "something";
    private static final JsonMapper MAPPER   = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @FunctionalInterface
    private interface SseScript {
        void write(OutputStream out) throws IOException, InterruptedException;
    }

    private ServerSocket server;
    private Thread       serverThread;

    @AfterEach
    void tearDown() throws IOException {
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (server != null) {
            server.close();
        }
    }

    private RemoteHttpReactivePolicyDecisionPoint startServer(SseScript script) throws IOException {
        server       = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        serverThread = new Thread(() -> {
                         try (val socket = server.accept()) {
                             drainRequestHeaders(socket.getInputStream());
                             val out = socket.getOutputStream();
                             out.write(
                                     ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n")
                                             .getBytes(UTF_8));
                             out.flush();
                             script.write(out);
                         } catch (IOException | InterruptedException ignored) {
                             // The client cancels or the test tears down; nothing to recover.
                         }
                     });
        serverThread.setDaemon(true);
        serverThread.start();
        val pdp = new RemoteHttpReactivePolicyDecisionPoint("http://127.0.0.1:" + server.getLocalPort(), "key",
                "secret", HttpClient.create());
        pdp.setFirstBackoffMillis(100);
        pdp.setMaxBackOffMillis(200);
        pdp.setTimeoutMillis(2000);
        return pdp;
    }

    private static void drainRequestHeaders(InputStream in) throws IOException {
        val last = new int[] { 0, 0, 0, 0 };
        int b;
        while ((b = in.read()) != -1) {
            last[0] = last[1];
            last[1] = last[2];
            last[2] = last[3];
            last[3] = b;
            if (last[0] == '\r' && last[1] == '\n' && last[2] == '\r' && last[3] == '\n') {
                return;
            }
        }
    }

    private static void writeChunk(OutputStream out, String frame) throws IOException {
        val bytes = frame.getBytes(UTF_8);
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(UTF_8));
        out.flush();
    }

    private static String decisionEvent(AuthorizationDecision decision) {
        return "data: " + MAPPER.writeValueAsString(decision) + "\n\n";
    }

    @Test
    @Timeout(20)
    @DisplayName("a stream that goes silent (no decision, no keep-alive) fails closed to INDETERMINATE")
    void whenStreamGoesSilentThenIndeterminate() throws Exception {
        val pdp = startServer(out -> {
            writeChunk(out, decisionEvent(AuthorizationDecision.PERMIT));
            Thread.sleep(5000);
        });
        pdp.setInactivityTimeoutMillis(300);

        val decisions = pdp.decide(AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE));

        StepVerifier.create(decisions).expectNext(AuthorizationDecision.PERMIT)
                .expectNext(AuthorizationDecision.INDETERMINATE).thenCancel().verify(Duration.ofSeconds(15));
    }

    @Test
    @Timeout(20)
    @DisplayName("keep-alive frames keep a quiet stream alive and never surface as decisions")
    void whenKeepAlivesArriveThenNoSpuriousIndeterminate() throws Exception {
        val pdp = startServer(out -> {
            writeChunk(out, decisionEvent(AuthorizationDecision.PERMIT));
            for (var i = 0; i < 8; i++) {
                Thread.sleep(150);
                writeChunk(out, ": keep-alive\n\n");
            }
            Thread.sleep(5000);
        });
        pdp.setInactivityTimeoutMillis(500);

        val decisions = pdp.decide(AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE));

        StepVerifier.create(decisions).expectNext(AuthorizationDecision.PERMIT).expectNoEvent(Duration.ofMillis(900))
                .thenCancel().verify(Duration.ofSeconds(15));
    }
}
