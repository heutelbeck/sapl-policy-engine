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
package io.sapl.server.openidauthzapi;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Caps the request body size on the OpenID Authorization API path. Mirrors
 * the {@code sapl.pdp.rsocket.max-inbound-payload-size} guard on the RSocket
 * transport: rejects requests whose declared {@code Content-Length} exceeds
 * the configured limit before any body bytes are read.
 * <p>
 * Requests sent with chunked transfer encoding (no Content-Length) are
 * passed through to the handler, which then reads at most
 * {@code maxRequestBodyBytes} bytes from the input stream before throwing.
 * Jackson allocates per JSON token, so the up-front Content-Length check
 * keeps the obvious oversized-payload vector cheap.
 */
final class OpenIdRequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxRequestBodyBytes;

    OpenIdRequestSizeLimitFilter(long maxRequestBodyBytes) {
        if (maxRequestBodyBytes <= 0L) {
            throw new IllegalArgumentException("maxRequestBodyBytes must be positive, got " + maxRequestBodyBytes);
        }
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        val declaredLength = request.getContentLengthLong();
        if (declaredLength > maxRequestBodyBytes) {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value(),
                    "Request body exceeds the configured limit of " + maxRequestBodyBytes + " bytes.");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/access/v1");
    }
}
