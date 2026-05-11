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

/**
 * One-shot authorization endpoint. Reads an
 * {@link AuthorizationSubscription} from the request body, evaluates it
 * once against the current policy bundle, and writes the resulting
 * {@code AuthorizationDecision} as JSON. The request thread is a
 * virtual thread provided by the Jetty pool; the call blocks directly
 * on the engine with no Reactor in the path.
 */
@Slf4j
@RequiredArgsConstructor
final class DecideOnceServlet extends HttpServlet {

    private static final String CONTENT_TYPE_JSON = "application/json";

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
        try {
            val decision = pdp.decideOnce(subscription);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(CONTENT_TYPE_JSON);
            mapper.writeValue(response.getOutputStream(), decision);
        } catch (Exception evaluationFailure) {
            log.warn("decideOnce evaluation failed: {}", evaluationFailure.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, evaluationFailure.getMessage());
        }
    }
}
