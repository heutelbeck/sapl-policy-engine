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

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Service for looking up users by their credentials.
 */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final SaplNodeProperties properties;
    private final PasswordEncoder    passwordEncoder;

    /**
     * Finds a user by Basic Auth username.
     *
     * @param username the username to search for
     * @return the user entry if found
     */
    public Optional<UserEntry> findByBasicUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return properties.getUsers().stream().filter(user -> user.getBasic() != null)
                .filter(user -> username.equals(user.getBasic().getUsername())).findFirst();
    }

    /**
     * Finds a user by API key, matching against encoded keys.
     *
     * @param rawApiKey the raw (unencoded) API key
     * @return the user entry if found and credentials match
     */
    public Optional<UserEntry> findByApiKey(String rawApiKey) {
        if (rawApiKey == null) {
            return Optional.empty();
        }
        for (val user : properties.getUsers()) {
            val encodedKey = user.getApiKey();
            if (encodedKey != null && passwordEncoder.matches(rawApiKey, encodedKey)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    /**
     * Converts a UserEntry to a SaplUser.
     * <p>
     * The pdpId is guaranteed to be set (normalized at config load time).
     *
     * @param userEntry the user entry from configuration
     * @return a SaplUser with the entry's id and pdpId
     */
    public SaplUser toSaplUser(UserEntry userEntry) {
        return new SaplUser(userEntry.getId(), userEntry.getPdpId());
    }

}
