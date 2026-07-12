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
package io.sapl.node.auth.apikey;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Servlet filter for SAPL API key authentication. Delegates token extraction
 * to an {@link AuthenticationConverter} (provided by {@link ApiKeyService})
 * and authentication to an {@link AuthenticationManager}.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationConverter converter;
    private final AuthenticationManager   authenticationManager;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            val candidate = converter.convert(request);
            if (candidate != null) {
                val authenticated = authenticationManager.authenticate(candidate);
                SecurityContextHolder.getContext().setAuthentication(authenticated);
            }
        } catch (AuthenticationException e) {
            // A presented-but-invalid API key is an authentication failure, not a
            // server error. This filter runs upstream of ExceptionTranslationFilter,
            // so a propagating AuthenticationException would surface as 500. Map it
            // to 401 here instead.
            SecurityContextHolder.clearContext();
            log.debug("API key authentication failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed.");
            return;
        }
        chain.doFilter(request, response);
    }
}
