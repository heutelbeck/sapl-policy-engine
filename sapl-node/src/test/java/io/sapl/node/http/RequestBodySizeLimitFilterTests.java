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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;

@DisplayName("RequestBodySizeLimitFilter")
class RequestBodySizeLimitFilterTests {

    private static final long LIMIT = 64L;

    private static final FilterChain DRAINING_CHAIN = (req, res) -> ((HttpServletRequest) req).getInputStream()
            .readAllBytes();

    private static MockHttpServletRequest chunkedRequest(int bodyBytes) {
        // Simulate chunked transfer encoding: a body is present but no
        // Content-Length is declared, so getContentLengthLong returns -1.
        val request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent(new byte[bodyBytes]);
        return request;
    }

    @Test
    @DisplayName("a chunked body exceeding the limit aborts reading with a too-large error")
    void whenChunkedBodyExceedsLimitThenReadingIsAbortedWithError() {
        val filter       = new RequestBodySizeLimitFilter(LIMIT);
        val request      = chunkedRequest((int) LIMIT * 4);
        val response     = new MockHttpServletResponse();
        val invokeFilter = (ThrowingCallable) () -> filter.doFilter(request, response, DRAINING_CHAIN);

        assertThatThrownBy(invokeFilter).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
    }

    @Test
    @DisplayName("a chunked body within the limit passes through")
    void whenChunkedBodyWithinLimitThenPassesThrough() {
        val filter   = new RequestBodySizeLimitFilter(LIMIT);
        val request  = chunkedRequest((int) LIMIT / 2);
        val response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, DRAINING_CHAIN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a declared Content-Length over the limit is rejected before the body is read")
    void whenDeclaredContentLengthExceedsLimitThenRejected() throws Exception {
        val filter  = new RequestBodySizeLimitFilter(LIMIT);
        val request = new MockHttpServletRequest();
        request.setContent(new byte[(int) LIMIT * 4]);
        val response = new MockHttpServletResponse();

        filter.doFilter(request, response, DRAINING_CHAIN);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
    }
}
