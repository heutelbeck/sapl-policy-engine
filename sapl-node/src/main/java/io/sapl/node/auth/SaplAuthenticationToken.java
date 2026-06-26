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

import static io.sapl.node.auth.SaplRoles.PDP_CLIENT_AUTHORITIES;

import java.io.Serial;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import io.sapl.api.SaplVersion;

/**
 * Authentication token carrying a SaplUser principal.
 */
public class SaplAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final SaplUser saplUser;
    private Object         credentials;

    /**
     * Creates an authentication token for the given SAPL user.
     *
     * @param saplUser the authenticated user
     * @param credentials the credentials used for authentication (may be null after
     * authentication)
     */
    public SaplAuthenticationToken(SaplUser saplUser, Object credentials) {
        super(PDP_CLIENT_AUTHORITIES);
        this.saplUser    = Objects.requireNonNull(saplUser, "SaplUser must not be null");
        this.credentials = credentials;
        setAuthenticated(true);
    }

    /**
     * Creates an authentication token for the given SAPL user without credentials.
     *
     * @param saplUser the authenticated user
     */
    public SaplAuthenticationToken(SaplUser saplUser) {
        this(saplUser, null);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public SaplUser getPrincipal() {
        return saplUser;
    }

    @Override
    public @NonNull String getName() {
        return saplUser.id();
    }

    /**
     * Returns the PDP identifier associated with this authenticated user.
     *
     * @return the PDP identifier
     */
    public String getPdpId() {
        return saplUser.pdpId();
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credentials = null;
    }

}
