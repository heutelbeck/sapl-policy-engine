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
package io.sapl.node.jetty;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Streaming authorization endpoint. Reads an
 * {@link AuthorizationSubscription} from the request body, opens a
 * SAPL {@code Stream<AuthorizationDecision>} on the engine, and writes
 * each emitted decision as a Server-Sent Event ({@code text/event-stream}
 * frame in {@code data: ...} format). The request thread is a virtual
 * thread; the loop blocks directly on {@code stream.awaitNext()} until
 * the consumer disconnects (write failure) or the JVM shuts down.
 */
@Slf4j
@RequiredArgsConstructor
final class DecideStreamServlet extends HttpServlet {

    private static final String CONTENT_TYPE_SSE = "text/event-stream";

    private final BlockingPolicyDecisionPoint pdp;
    private final JsonMapper                  mapper;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthorizationSubscription subscription;
        try {
            subscription = mapper.readValue(request.getInputStream(), AuthorizationSubscription.class);
        } catch (Exception parseFailure) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, parseFailure.getMessage());
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE_SSE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        val writer = response.getWriter();

        try (val stream = pdp.decide(subscription)) {
            while (!Thread.currentThread().isInterrupted()) {
                val decision = stream.awaitNext();
                if (decision == null) {
                    return;
                }
                writer.write("data:");
                mapper.writeValue(writer, decision);
                writer.write("\n\n");
                writer.flush();
                if (writer.checkError()) {
                    return;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception streamFailure) {
            log.debug("Decision stream terminated: {}", streamFailure.getMessage());
        }
    }
}
