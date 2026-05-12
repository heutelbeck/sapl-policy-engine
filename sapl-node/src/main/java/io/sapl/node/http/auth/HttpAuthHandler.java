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
package io.sapl.node.http.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Per-request authentication for the bypass-Spring HTTP PDP endpoint.
 * Inspects the {@code Authorization} header, validates against the configured
 * user store, and returns the resolved tenant identifier. Implementations are
 * expected to cache results so that line-rate request throughput does not
 * pay an Argon2 verification per request.
 */
public interface HttpAuthHandler {

    /**
     * Authenticates the request and resolves the PDP id.
     *
     * @param request the incoming servlet request
     * @return the authentication result carrying the resolved PDP id
     * @throws HttpAuthenticationException when authentication is required and
     * fails, or when no credentials are supplied while at least one
     * authentication mechanism is mandatory
     */
    HttpAuthResult authenticate(HttpServletRequest request);

    /**
     * Result of a successful authentication.
     *
     * @param pdpId the tenant identifier to use for the PDP call
     */
    record HttpAuthResult(String pdpId) {}
}
