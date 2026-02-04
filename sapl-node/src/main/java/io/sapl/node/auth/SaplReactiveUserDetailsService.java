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

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Reactive user details service for Basic Auth, backed by UserLookupService.
 * <p>
 * The user's pdpId is stored as a custom attribute to be extracted later during
 * authentication success handling.
 * <p>
 * Note: This is not a Spring @Service to avoid triggering Spring Security's
 * auto-configuration which requires a ReactiveUserDetailsPasswordService.
 */
@RequiredArgsConstructor
public class SaplReactiveUserDetailsService implements ReactiveUserDetailsService {

    static final String ROLE_PDP_CLIENT = "PDP_CLIENT";
    static final String ATTR_USER_ID    = "userId";
    static final String ATTR_PDP_ID     = "pdpId";

    private final UserLookupService userLookupService;

    @Override
    public @NonNull Mono<UserDetails> findByUsername(@NonNull String username) {
        val userEntryOpt = userLookupService.findByBasicUsername(username);
        if (userEntryOpt.isEmpty()) {
            return Mono.error(new UsernameNotFoundException("User not found: " + username));
        }

        val userEntry = userEntryOpt.get();
        val basic     = userEntry.getBasic();

        return Mono.just(User.builder().username(basic.getUsername()).password(basic.getSecret()).roles(ROLE_PDP_CLIENT)
                .build());
    }

    /**
     * Resolves the SaplUser for a given username after successful authentication.
     *
     * @param username the authenticated username
     * @return the SaplUser if found, empty otherwise
     */
    public Mono<SaplUser> resolveSaplUser(String username) {
        val userEntryOpt = userLookupService.findByBasicUsername(username);
        if (userEntryOpt.isEmpty()) {
            return Mono.empty();
        }
        return Mono.just(userLookupService.toSaplUser(userEntryOpt.get()));
    }

}
