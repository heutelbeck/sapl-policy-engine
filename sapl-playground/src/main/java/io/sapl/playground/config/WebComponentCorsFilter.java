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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * CORS filter that allows cross-origin requests to the web component and
 * Vaadin UIDL/push endpoints. Required for embedding the exported
 * {@code <sapl-playground>} custom element on external sites.
 */
@Component
class WebComponentCorsFilter extends OncePerRequestFilter {

    private static final String ALLOWED_HEADERS = "Content-Type, Accept, X-Requested-With, v-r";

    private final Set<String> allowedOrigins;

    WebComponentCorsFilter(EmbedCorsProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.allowedOrigins());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        val origin        = request.getHeader("Origin");
        val matchedOrigin = origin != null ? findAllowedOrigin(origin) : null;
        if (matchedOrigin != null) {
            response.setHeader("Access-Control-Allow-Origin", matchedOrigin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Access-Control-Allow-Methods", "GET, POST");
                response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String findAllowedOrigin(String origin) {
        for (val allowed : allowedOrigins) {
            if (allowed.equals(origin)) {
                return allowed;
            }
        }
        return null;
    }

    @ConfigurationProperties(prefix = "sapl.embed.cors")
    record EmbedCorsProperties(@DefaultValue( {}) List<String> allowedOrigins){}
}
