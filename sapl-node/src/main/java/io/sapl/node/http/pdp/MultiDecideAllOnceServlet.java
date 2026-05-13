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
package io.sapl.node.http.pdp;

import java.io.IOException;
import java.io.Serial;

import org.jspecify.annotations.NonNull;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.stream.Stream;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthenticationException;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bypass-Spring servlet for {@code POST /api/pdp/multi-decide-all-once}: a
 * single bundled {@link MultiAuthorizationDecision} for the supplied
 * multi-subscription. Built on the blocking PDP's
 * {@link Stream}-based {@code decideAll} with {@code awaitNext()} for the
 * first emission, then close.
 */
@Slf4j
@RequiredArgsConstructor
public class MultiDecideAllOnceServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final BlockingPolicyDecisionPoint pdp;
    private final HttpAuthHandler             authHandler;
    private final JsonMapper                  mapper;

    @Override
    protected void doPost(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response)
            throws ServletException, IOException {
        String pdpId;
        try {
            pdpId = authHandler.authenticate(request).pdpId();
        } catch (HttpAuthenticationException e) {
            log.debug("HTTP authentication failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed.");
            return;
        }

        MultiAuthorizationSubscription subscription;
        try (val in = request.getInputStream()) {
            subscription = mapper.readValue(in, MultiAuthorizationSubscription.class);
        } catch (IOException | JacksonException e) {
            log.debug("Failed to parse multi-subscription: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed multi-subscription.");
            return;
        }

        MultiAuthorizationDecision decision;
        try (Stream<MultiAuthorizationDecision> stream = pdp.decideAll(subscription, pdpId)) {
            decision = stream.awaitNext();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            decision = MultiAuthorizationDecision.indeterminate();
        }
        if (decision == null) {
            decision = MultiAuthorizationDecision.indeterminate();
        }

        // Serialize first so the status code reflects the actual outcome.
        // Writing 200 then mapper.writeValue would leave the client with
        // "200 OK" plus a truncated body on a Jackson failure mid-write.
        val body = mapper.writeValueAsBytes(decision);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setContentLength(body.length);
        try (val out = response.getOutputStream()) {
            out.write(body);
        }
    }
}
