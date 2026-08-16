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
package io.sapl.attributeapi.auth.apikey;

import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

public class ApiKeyAuthenticationProvider implements AuthenticationProvider {
    private static final String               ERROR_NO_API_KEY = "Invalid api key.";
    private final ApiKeyAuthenticationService service;

    public ApiKeyAuthenticationProvider(ApiKeyAuthenticationService service) {
        this.service = service;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String key = Objects.requireNonNull(authentication.getCredentials()).toString();
        try {
            Optional<AttributeApiUserDetails> user = service.findByApiKey(key);

            if (user.isPresent()) {
                AttributeApiUserDetails details = user.get();
                return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
            } else {
                throw new BadCredentialsException(ERROR_NO_API_KEY);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
