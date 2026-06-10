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
package io.sapl.node.http.pdp;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * POST-only servlet base. Implements {@link Servlet} directly to avoid the
 * Serializable contract carried by {@code GenericServlet}, which is dead
 * weight for these bypass-Spring endpoints.
 */
public abstract class AbstractBypassServlet implements Servlet {

    private static final String METHOD_POST = "POST";

    private @Nullable ServletConfig config;

    @Override
    public void init(@NonNull ServletConfig servletConfig) {
        this.config = servletConfig;
    }

    @Override
    public @Nullable ServletConfig getServletConfig() {
        return config;
    }

    @Override
    public @NonNull String getServletInfo() {
        return "";
    }

    @Override
    public void destroy() {
        // Dependencies are managed by Spring.
    }

    @Override
    public final void service(@NonNull ServletRequest request, @NonNull ServletResponse response)
            throws ServletException, IOException {
        val httpRequest  = (HttpServletRequest) request;
        val httpResponse = (HttpServletResponse) response;
        if (!METHOD_POST.equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        handlePost(httpRequest, httpResponse);
    }

    protected abstract void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException;
}
