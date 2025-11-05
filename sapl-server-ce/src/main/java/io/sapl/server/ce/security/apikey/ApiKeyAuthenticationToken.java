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

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import io.sapl.api.SaplVersion;

import java.util.Collection;

/**
 * Authentication token for SAPL API keys.
 * The unauthenticated constructor is used before validation.
 * The authenticated constructor is used after validation and carries
 * authorities.
 */
@ToString
@EqualsAndHashCode(callSuper = true)
public final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private String credentials;
    private String principal;

    /** Creates an unauthenticated token (before API key validation). */
    public ApiKeyAuthenticationToken(String clientCredentialsId, String key) {
        super(null);
        this.principal   = clientCredentialsId;
        this.credentials = key;
        setAuthenticated(false);
    }

    /**
     * Creates an authenticated token (after API key validation) with authorities.
     */
    public ApiKeyAuthenticationToken(String clientCredentialsId,
            String key,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal   = clientCredentialsId;
        this.credentials = key;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /**
     * Disallow setting authenticated to true after construction.
     */
    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        if (isAuthenticated) {
            throw new IllegalArgumentException("Use the authenticated constructor with authorities.");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credentials = null;
    }
}
