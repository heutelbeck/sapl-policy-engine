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
package io.sapl.node.apikey;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import io.sapl.node.auth.SaplAuthenticationToken;

/**
 * Authentication manager that accepts an already-validated API key
 * authentication. The actual API key check happens in {@link ApiKeyService}
 * and produces a {@link SaplAuthenticationToken}; the manager marks only
 * tokens of that exact shape as authenticated. Any other Authentication
 * type reaching this manager indicates a misrouted filter chain and is
 * returned untrusted.
 */
public class ApiKeyAuthenticationManager implements AuthenticationManager {

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (authentication instanceof SaplAuthenticationToken sapl) {
            sapl.setAuthenticated(true);
        }
        return authentication;
    }
}
