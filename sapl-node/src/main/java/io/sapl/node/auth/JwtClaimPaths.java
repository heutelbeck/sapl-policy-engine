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

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Resolves the configured PDP id claim from a JWT by exact name or by
 * dot-separated path.
 * <p>
 * An exact top-level claim always wins, so configurations whose claim name
 * contains literal dots keep working unchanged. Otherwise the name is read as
 * a path into nested claim objects, for example
 * {@code resource_access.sapl.tenant}. Only scalar leaf values (string,
 * number, boolean) resolve, structured values resolve to null.
 */
@UtilityClass
public class JwtClaimPaths {

    private static final String ERROR_INVALID_PATH = "Claim path must be a non-blank claim name or dot-separated path without empty segments, got '%s'.";

    /**
     * Validates a configured claim name or path so a malformed value fails at
     * startup instead of silently never matching a token.
     *
     * @param path the configured claim name or dot-separated path
     * @throws IllegalArgumentException if the path is null, blank, or contains
     * empty segments (leading, trailing, or doubled dots)
     */
    public static void requireValidPath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(ERROR_INVALID_PATH.formatted(path));
        }
        for (val segment : path.split("\\.", -1)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException(ERROR_INVALID_PATH.formatted(path));
            }
        }
    }

    /**
     * Resolves a claim value as a string, first by exact top-level name, then
     * by interpreting the name as a dot-separated path into nested claims.
     *
     * @param jwt the decoded token
     * @param path the claim name or dot-separated path
     * @return the scalar claim value as a string, or null when absent or not a
     * scalar
     */
    public static @Nullable String resolveStringClaim(Jwt jwt, String path) {
        if (jwt.hasClaim(path)) {
            return jwt.getClaimAsString(path);
        }
        Object current = jwt.getClaims();
        for (val segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> nested)) {
                return null;
            }
            current = nested.get(segment);
        }
        return switch (current) {
        case String stringValue   -> stringValue;
        case Number numberValue   -> numberValue.toString();
        case Boolean booleanValue -> booleanValue.toString();
        case null, default        -> null;
        };
    }
}
