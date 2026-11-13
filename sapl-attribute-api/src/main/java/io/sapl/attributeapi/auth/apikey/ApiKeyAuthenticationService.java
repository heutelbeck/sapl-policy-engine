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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties.UserEntry;
import lombok.val;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import java.util.UUID;

/**
 * The authentication service of the Attribute API server.
 */
public class ApiKeyAuthenticationService {
    private static final String SAPL_PREFIX = "sapl_";

    private final AttributeApiSecurityProperties         properties;
    private final PasswordEncoder                        encoder;
    private final String                                 dummyArgon2;
    private final Cache<String, AttributeApiUserDetails> cache = Caffeine.newBuilder().maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(5)).build();

    /**
     * Creates the authentication service object for the current instance of the
     * Attribute API server.
     *
     * @param properties The security properties of the current instance with all
     * relevant settings.
     * @param encoder The password encoder to use. Usually it's strong encryption
     * with Argon2.
     */
    public ApiKeyAuthenticationService(AttributeApiSecurityProperties properties, PasswordEncoder encoder) {
        this.properties = properties;
        this.encoder    = encoder;

        // Always execute an Argon2 encoding, so that an attacker cannot measure the
        // timings if a key is missing
        this.dummyArgon2 = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Finds the user of an API key. The API key is like {@code sapl_<id>_<secret>}.
     * Valid keys are cached for five minutes to reduce load. One Argon2 checks runs
     * for every key with a valid format. This prevents that the response times does
     * not differ too much whether an id is configured or not.
     *
     * @param rawKey the complete API key from the request.
     * @return the user of key. Empty if the key is invalid.
     * @throws NoSuchAlgorithmException if SHA-256 is missing. The JVM usually provides it.
     */
    public Optional<AttributeApiUserDetails> findByApiKey(String rawKey) throws NoSuchAlgorithmException {
        val cacheKey   = sha256(rawKey);
        val cachedUser = cache.getIfPresent(cacheKey);

        if (cachedUser != null) {
            return Optional.of(cachedUser);
        }

        val apiKeyId = extractApiKeyId(rawKey);
        if (apiKeyId == null) {
            return Optional.empty();
        }

        val candidate      = properties.getApiKeyIdIndex().get(apiKeyId);
        val hasCandidate   = candidate != null && candidate.getApiKey() != null;
        val encodedToCheck = hasCandidate ? candidate.getApiKey() : dummyArgon2;
        val matches        = encoder.matches(rawKey, encodedToCheck);

        if (hasCandidate && matches) {
            val details = toUserDetails(candidate);
            cache.put(cacheKey, details);
            return Optional.of(details);
        }
        return Optional.empty();
    }

    private static AttributeApiUserDetails toUserDetails(UserEntry user) {
        return new AttributeApiUserDetails(user.getId(), null, user.getPdpId());
    }

    // Extract the public id (middle part) of the api key. Assuming the prefix is
    // set via SAPL_PREFIX
    private static String extractApiKeyId(String rawKey) {
        if (!rawKey.startsWith(SAPL_PREFIX)) {
            return null;
        }
        val publicIdEndId = rawKey.indexOf('_', SAPL_PREFIX.length());
        if (publicIdEndId <= SAPL_PREFIX.length()) {
            return null;
        }
        return rawKey.substring(SAPL_PREFIX.length(), publicIdEndId);
    }

    // Hash the raw key in case of a heap dump exposure
    private static String sha256(String rawKey) throws NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8)));
    }
}
