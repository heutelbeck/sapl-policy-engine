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

import io.sapl.node.limits.ConcurrencyLimit;
import io.sapl.node.limits.RejectionReporter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Caps the number of concurrently open SSE decision streams. Registered only
 * for the streaming PDP routes and only when a limit is configured, so an
 * unlimited node carries no admission code in its request path. Over-cap
 * requests are shed fail-closed with 503 and a Retry-After header before
 * authentication and body parsing.
 * <p>
 * The permit follows the stream's lifecycle: the request is wrapped so that
 * the servlet's {@code startAsync()} registers an {@link AsyncListener}
 * releasing the permit when the async context completes, whichever of pump
 * teardown, token expiry, or shutdown drain gets there first. Requests that
 * never start async processing (authentication or parse failures) release on
 * filter exit. Permit release is idempotent, so the overlap of the two paths
 * cannot double-free a slot.
 */
final class SseStreamLimitFilter extends OncePerRequestFilter {

    static final String HEADER_RETRY_AFTER          = "Retry-After";
    static final String RETRY_AFTER_SECONDS         = "10";
    static final String ERROR_STREAM_LIMIT_EXCEEDED = "The server reached its configured limit of concurrent decision streams.";

    private final ConcurrencyLimit  limit;
    private final RejectionReporter reporter;

    SseStreamLimitFilter(ConcurrencyLimit limit, RejectionReporter reporter) {
        this.limit    = limit;
        this.reporter = reporter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        val permit = limit.tryAcquire();
        if (permit == null) {
            reporter.onRejection();
            response.setHeader(HEADER_RETRY_AFTER, RETRY_AFTER_SECONDS);
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), ERROR_STREAM_LIMIT_EXCEEDED);
            return;
        }
        val trackedRequest = new PermitTrackingRequest(request, permit);
        try {
            chain.doFilter(trackedRequest, response);
        } finally {
            if (!trackedRequest.permitFollowsAsyncLifecycle) {
                permit.close();
            }
        }
    }

    private static final class PermitTrackingRequest extends HttpServletRequestWrapper {

        private final ConcurrencyLimit.Permit permit;

        private volatile boolean permitFollowsAsyncLifecycle;

        PermitTrackingRequest(HttpServletRequest request, ConcurrencyLimit.Permit permit) {
            super(request);
            this.permit = permit;
        }

        @Override
        public AsyncContext startAsync() {
            return bindPermit(super.startAsync());
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
            return bindPermit(super.startAsync(servletRequest, servletResponse));
        }

        private AsyncContext bindPermit(AsyncContext asyncContext) {
            asyncContext.addListener(new PermitReleasingListener(permit));
            permitFollowsAsyncLifecycle = true;
            return asyncContext;
        }
    }

    private record PermitReleasingListener(ConcurrencyLimit.Permit permit) implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            permit.close();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            permit.close();
        }

        @Override
        public void onError(AsyncEvent event) {
            permit.close();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            // The permit stays bound to the original async lifecycle.
        }
    }
}
