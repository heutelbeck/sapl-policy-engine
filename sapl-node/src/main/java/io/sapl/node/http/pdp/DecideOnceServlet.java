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

import org.jspecify.annotations.NonNull;

import tools.jackson.core.JacksonException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthenticationException;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bypass-Spring servlet for {@code POST /api/pdp/decide-once}. Reads an
 * {@link AuthorizationSubscription} as JSON, calls the blocking PDP directly,
 * and writes the {@link AuthorizationDecision} as JSON. Authentication is
 * delegated to {@link HttpAuthHandler} which caches results.
 */
@Slf4j
@RequiredArgsConstructor
public class DecideOnceServlet extends AbstractBypassServlet {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final BlockingPolicyDecisionPoint pdp;
    private final HttpAuthHandler             authHandler;
    private final JsonMapper                  mapper;

    @Override
    protected void handlePost(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response)
            throws ServletException, IOException {
        String pdpId;
        try {
            pdpId = authHandler.authenticate(request).pdpId();
        } catch (HttpAuthenticationException e) {
            log.debug("HTTP authentication failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed.");
            return;
        }

        AuthorizationSubscription subscription;
        try (val in = request.getInputStream()) {
            subscription = mapper.readValue(in, AuthorizationSubscription.class);
        } catch (IOException | JacksonException e) {
            log.debug("Failed to parse authorization subscription: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed authorization subscription.");
            return;
        }

        val decision = pdp.decideOnce(subscription, pdpId);
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
