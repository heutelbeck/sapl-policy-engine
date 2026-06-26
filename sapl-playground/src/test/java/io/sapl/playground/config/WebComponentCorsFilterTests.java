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
package io.sapl.playground.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.playground.config.WebComponentCorsFilter.EmbedCorsProperties;
import lombok.val;

@DisplayName("Web component CORS filter")
class WebComponentCorsFilterTests {

    private static final String ALLOWED_ORIGIN = "https://embedder.example.org";

    private static WebComponentCorsFilter filterAllowing(String origin) {
        return new WebComponentCorsFilter(new EmbedCorsProperties(List.of(origin)));
    }

    @Test
    @DisplayName("reflecting an allowed origin also emits Vary: Origin so shared caches cannot serve one origin's CORS headers to another")
    void whenOriginAllowedThenVaryOriginHeaderIsEmitted() throws Exception {
        val filter  = filterAllowing(ALLOWED_ORIGIN);
        val request = new MockHttpServletRequest("GET", "/embed");
        request.addHeader("Origin", ALLOWED_ORIGIN);
        val response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response).satisfies(reflected -> {
            assertThat(reflected.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
            assertThat(reflected.getHeaders("Vary")).contains("Origin");
        });
    }
}
