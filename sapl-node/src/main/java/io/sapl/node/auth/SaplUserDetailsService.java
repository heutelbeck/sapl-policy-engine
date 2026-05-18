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

import static io.sapl.node.auth.SaplRoles.PDP_CLIENT;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Servlet user-details service for Basic Auth, backed by
 * {@link UserLookupService}.
 * <p>
 * Note: not annotated as a Spring component to avoid triggering Spring
 * Security's auto-configuration that requires a
 * {@code UserDetailsPasswordService}.
 */
@RequiredArgsConstructor
public class SaplUserDetailsService implements UserDetailsService {

    static final String ERROR_USER_NOT_FOUND = "User not found: %s";

    private final UserLookupService userLookupService;

    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) {
        val userEntryOpt = userLookupService.findByBasicUsername(username);
        if (userEntryOpt.isEmpty()) {
            throw new UsernameNotFoundException(ERROR_USER_NOT_FOUND.formatted(username));
        }
        val basic = userEntryOpt.get().getBasic();
        return User.builder().username(basic.getUsername()).password(basic.getSecret()).roles(PDP_CLIENT).build();
    }

    /**
     * Resolves the SaplUser for a given username after successful authentication.
     *
     * @param username the authenticated username
     * @return the SaplUser if found, empty otherwise
     */
    public Optional<SaplUser> resolveSaplUser(String username) {
        return userLookupService.findByBasicUsername(username).map(userLookupService::toSaplUser);
    }
}
