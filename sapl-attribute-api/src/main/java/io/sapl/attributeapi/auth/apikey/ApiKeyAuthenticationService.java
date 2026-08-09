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

import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import io.sapl.attributeapi.auth.AttributeApiUserDetails;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@RequiredArgsConstructor
public class ApiKeyAuthenticationService {
    private final AttributeApiSecurityProperties properties;

    private static String sha256(String rawKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
    }

    public Optional<AttributeApiUserDetails> findByApiKey(String rawKey) throws NoSuchAlgorithmException {
        String hash = sha256(rawKey);
        return properties.getUsers().stream()
                .filter(user -> user.getKey() != null && hash.equals(user.getKey().getHash())).findFirst()
                .map(user -> new AttributeApiUserDetails(user.getId(), null, user.getTenantId()));
    }
}
