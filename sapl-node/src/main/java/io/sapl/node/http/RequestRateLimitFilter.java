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
package io.sapl.node.http;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.sapl.node.limits.RateLimit;
import io.sapl.node.limits.RejectionReporter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Global request-rate limit for the unary PDP endpoints. Registered only when
 * a rate is configured, so an unlimited node carries no admission code in its
 * request path. Over-rate requests are shed fail-closed with 429 and a
 * Retry-After header before authentication and body parsing.
 */
final class RequestRateLimitFilter extends OncePerRequestFilter {

    static final String HEADER_RETRY_AFTER        = "Retry-After";
    static final String RETRY_AFTER_SECONDS       = "1";
    static final String ERROR_RATE_LIMIT_EXCEEDED = "The server reached its configured request rate limit.";

    private final RateLimit         rateLimit;
    private final RejectionReporter reporter;

    RequestRateLimitFilter(RateLimit rateLimit, RejectionReporter reporter) {
        this.rateLimit = rateLimit;
        this.reporter  = reporter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        if (!rateLimit.tryAcquire()) {
            reporter.onRejection();
            response.setHeader(HEADER_RETRY_AFTER, RETRY_AFTER_SECONDS);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), ERROR_RATE_LIMIT_EXCEEDED);
            return;
        }
        chain.doFilter(request, response);
    }
}
