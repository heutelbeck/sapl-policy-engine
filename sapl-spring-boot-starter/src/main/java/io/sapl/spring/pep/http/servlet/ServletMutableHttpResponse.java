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
package io.sapl.spring.pep.http.servlet;

import io.sapl.api.SaplVersion;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import io.sapl.spring.pep.http.MutableHttpResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.io.Serial;

/**
 * Servlet-backed {@link MutableHttpResponse}. Routes status and header
 * mutations to the underlying {@link HttpServletResponse}. The
 * {@link #headers()} view is a snapshot of the current response headers
 * at the time of the call. Mutations to that snapshot are pushed back
 * onto the underlying response.
 */
@RequiredArgsConstructor
public final class ServletMutableHttpResponse implements MutableHttpResponse {

    private final HttpServletResponse delegate;

    @Override
    public void setStatusCode(HttpStatusCode status) {
        delegate.setStatus(status.value());
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return HttpStatusCode.valueOf(delegate.getStatus());
    }

    @Override
    public void setHeader(String name, String value) {
        delegate.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        delegate.addHeader(name, value);
    }

    @Override
    public void writeBody(String contentType, String body) {
        delegate.setContentType(contentType);
        try {
            delegate.getWriter().write(body);
            delegate.flushBuffer();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public HttpHeaders headers() {
        val headers = new HttpHeaders();
        for (val name : delegate.getHeaderNames()) {
            for (val value : delegate.getHeaders(name)) {
                headers.add(name, value);
            }
        }
        return new WriteThroughHttpHeaders(headers, delegate);
    }

    /**
     * Header view that propagates mutations to the underlying servlet
     * response. Reads return the snapshot taken at construction time.
     * Writes (set, add, remove) push through to the response, which is
     * what the container will commit.
     */
    private static final class WriteThroughHttpHeaders extends HttpHeaders {

        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        private final transient HttpServletResponse target;

        WriteThroughHttpHeaders(HttpHeaders snapshot, HttpServletResponse target) {
            super(snapshot);
            this.target = target;
        }

        @Override
        public void set(@NonNull String headerName, String headerValue) {
            super.set(headerName, headerValue);
            target.setHeader(headerName, headerValue);
        }

        @Override
        public void add(@NonNull String headerName, String headerValue) {
            super.add(headerName, headerValue);
            target.addHeader(headerName, headerValue);
        }
    }
}
