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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Caps inbound HTTP request body size. Requests whose declared
 * {@code Content-Length} exceeds the configured limit are rejected before any
 * body bytes are read. Requests without a {@code Content-Length} (chunked
 * transfer encoding) are wrapped so that the actual number of body bytes read
 * is counted and reading aborts once the limit is exceeded, so chunked uploads
 * cannot bypass the cap. Mirrors the
 * {@code sapl.pdp.rsocket.max-inbound-payload-size} guard on the RSocket
 * transport. Path scoping is the responsibility of the registration.
 */
public final class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxRequestBodyBytes;

    public RequestBodySizeLimitFilter(long maxRequestBodyBytes) {
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
        chain.doFilter(new LimitingRequest(request, maxRequestBodyBytes), response);
    }

    private static final class LimitingRequest extends HttpServletRequestWrapper {

        private final long limit;

        LimitingRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitingServletInputStream(getRequest().getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            val encoding = getCharacterEncoding();
            val charset  = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long               limit;
        private long                     count;

        LimitingServletInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit    = limit;
        }

        @Override
        public int read() throws IOException {
            val read = delegate.read();
            if (read != -1 && ++count > limit) {
                throw tooLarge(limit);
            }
            return read;
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            val read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count += read;
                if (count > limit) {
                    throw tooLarge(limit);
                }
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private static RequestBodyTooLargeException tooLarge(long maxRequestBodyBytes) {
            return new RequestBodyTooLargeException(
                    "Request body exceeds the configured limit of " + maxRequestBodyBytes + " bytes.");
        }
    }
}
