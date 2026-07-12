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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.node.limits.RateLimit;
import io.sapl.node.limits.RejectionReporter;
import jakarta.servlet.FilterChain;
import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestRateLimitFilter")
class RequestRateLimitFilterTests {

    private static final String POST = "POST";
    private static final String PATH = "/api/pdp/decide-once";

    @Mock
    private FilterChain chain;

    @Test
    @DisplayName("requests within the configured rate pass through to the servlet")
    void whenWithinRateThenRequestPasses() throws Exception {
        val filter = new RequestRateLimitFilter(new RateLimit(1), new RejectionReporter("test", "test limit", null));
        filter.doFilter(new MockHttpServletRequest(POST, PATH), new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("over-rate requests are shed with 429 and Retry-After before reaching the servlet")
    void whenOverRateThenRequestShedWithTooManyRequests() throws Exception {
        val filter = new RequestRateLimitFilter(new RateLimit(1), new RejectionReporter("test", "test limit", null));
        filter.doFilter(new MockHttpServletRequest(POST, PATH), new MockHttpServletResponse(), chain);

        val response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(POST, PATH), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(RequestRateLimitFilter.HEADER_RETRY_AFTER))
                .isEqualTo(RequestRateLimitFilter.RETRY_AFTER_SECONDS);
        verify(chain, times(1)).doFilter(any(), any());
    }
}
