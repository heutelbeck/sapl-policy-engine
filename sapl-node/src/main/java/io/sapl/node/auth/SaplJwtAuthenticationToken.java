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
package io.sapl.node.auth;

import java.io.Serial;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import io.sapl.api.SaplVersion;

/**
 * JWT authentication token that provides access to the PDP identifier from JWT
 * claims.
 * <p>
 * This token extends Spring Security's {@link JwtAuthenticationToken} to add
 * SAPL-specific functionality for multi-tenant PDP routing.
 */
public class SaplJwtAuthenticationToken extends JwtAuthenticationToken {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final String pdpId;

    /**
     * Creates a JWT authentication token with the specified PDP identifier.
     *
     * @param jwt the JWT
     * @param authorities the authorities granted to the principal
     * @param pdpId the PDP identifier for routing
     */
    public SaplJwtAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, String pdpId) {
        super(jwt, authorities);
        this.pdpId = pdpId;
    }

    /**
     * Returns the PDP identifier associated with this authenticated user.
     *
     * @return the PDP identifier
     */
    public String getPdpId() {
        return pdpId;
    }

}
