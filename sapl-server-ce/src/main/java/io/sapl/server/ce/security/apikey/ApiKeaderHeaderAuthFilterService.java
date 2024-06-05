/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

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
public class ApiKeaderHeaderAuthFilterService extends GenericFilterBean {
    private final ApiKeyService apiKeyService;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        // checking apiKey Header only if the request is not yet authorized
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var request  = (HttpServletRequest) servletRequest;
            var response = (HttpServletResponse) servletResponse;
            // if header token is not valid, send un-authorized error back
            String apiKey = request.getHeader(apiKeyService.getApiKeyHeaderName());
            if (StringUtils.isNotEmpty(apiKey)) {
                if (apiKeyService.isValidApiKey(apiKey)) {
                    SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken());
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
