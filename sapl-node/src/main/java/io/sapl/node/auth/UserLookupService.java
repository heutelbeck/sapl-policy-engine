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
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.val;

/**
 * Service for looking up users by their credentials.
 */
@Service
public class UserLookupService {

    private static final String SAPL_PREFIX = "sapl_";

    private final SaplNodeProperties properties;
    private final PasswordEncoder    passwordEncoder;
    private final String             dummyArgon2Hash;

    public UserLookupService(SaplNodeProperties properties, PasswordEncoder passwordEncoder) {
        this.properties      = properties;
        this.passwordEncoder = passwordEncoder;
        this.dummyArgon2Hash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

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
     * Verifies Basic-auth credentials with constant work.
     * <p>
     * Exactly one Argon2 verification runs whether or not the username exists
     * (the user's secret when present, a fixed dummy hash otherwise), so an
     * unknown username cannot be distinguished from a known one with a wrong
     * password by response timing.
     *
     * @param username the presented username
     * @param password the presented raw password
     * @return the matching user entry, or empty if the username is unknown or
     * the password does not match
     */
    public Optional<UserEntry> verifyBasicCredentials(String username, String password) {
        val userOpt = findByBasicUsername(username);
        val secret  = userOpt.map(user -> user.getBasic().getSecret()).orElse(null);
        // A found user with a missing or blank secret must not short-circuit the
        // Argon2 verification (which would leak the username via timing); verify
        // against the dummy hash so exactly one full-cost verification runs on
        // every path, and such a user can never authenticate.
        val hasSecret = secret != null && !secret.isBlank();
        val encoded   = hasSecret ? secret : dummyArgon2Hash;
        val matches   = passwordEncoder.matches(password, encoded);
        return userOpt.isPresent() && hasSecret && matches ? userOpt : Optional.empty();
    }

    /**
     * Finds a user by API key.
     * <p>
     * Wire format: {@code sapl_<id>_<secret>}. The {@code id} segment is looked
     * up in the user configuration's {@code api-key-id} index, so a match needs
     * one index lookup and a single Argon2 verification. A key whose id is not
     * indexed is rejected.
     * <p>
     * The miss path performs a dummy Argon2 verify so response latency does
     * not reveal whether a given {@code api-key-id} exists in the config.
     *
     * @param rawApiKey the raw (unencoded) API key
     * @return the user entry if found and credentials match
     */
    public Optional<UserEntry> findByApiKey(String rawApiKey) {
        if (rawApiKey == null) {
            return Optional.empty();
        }
        val apiKeyId = extractApiKeyId(rawApiKey);
        if (apiKeyId == null) {
            return Optional.empty();
        }
        val candidate    = properties.getApiKeyIdIndex().get(apiKeyId);
        val hasCandidate = candidate != null && candidate.getApiKey() != null;
        // Always run exactly one Argon2 verification (dummy hash when absent), so
        // timing does not leak configured ids.
        val encodedToCheck = hasCandidate ? candidate.getApiKey() : dummyArgon2Hash;
        val matches        = passwordEncoder.matches(rawApiKey, encodedToCheck);
        if (hasCandidate && matches) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static String extractApiKeyId(String rawApiKey) {
        if (!rawApiKey.startsWith(SAPL_PREFIX)) {
            return null;
        }
        val firstSep = rawApiKey.indexOf('_', SAPL_PREFIX.length());
        if (firstSep <= SAPL_PREFIX.length()) {
            return null;
        }
        return rawApiKey.substring(SAPL_PREFIX.length(), firstSep);
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
