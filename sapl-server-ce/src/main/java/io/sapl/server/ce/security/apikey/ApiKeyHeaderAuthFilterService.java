/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.security.apikey;

import java.io.IOException;

import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.security.core.AuthenticationException;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class ApiKeyHeaderAuthFilterService extends GenericFilterBean {
    private final ApiKeyService apiKeyService;

    /**
     * This Method enabled the Api-Key authentication for HTTP requests. Api tokens
     * are recognized by when a sapl_ Bearer Token is present in the Authentication
     * header.
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        // checking apiKey Header only if the request is not yet authorized
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            final var request     = (HttpServletRequest) servletRequest;
            final var apikeyToken = ApiKeyService.getApiKeyToken(request);
            // if header token is not valid, send un-authorized error
            if (apikeyToken != null) {
                try {
                    SecurityContextHolder.getContext().setAuthentication(apiKeyService.checkApiKey(apikeyToken));
                } catch (AuthenticationException ex) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
