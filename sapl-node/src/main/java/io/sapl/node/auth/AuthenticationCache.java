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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import lombok.val;

/**
 * The outcomes of authentication, one cache for the HTTP and the RSocket API,
 * so that repeated credentials cost one Argon2 verification per lifetime of
 * their outcome, whichever transport presents them. The keys
 * {@code io.sapl.node.http.auth-cache.*} configure it for both transports.
 * <p>
 * The key of an outcome is the SHA-256 hash of the transport and the
 * credentials as presented, so the raw credentials are not kept in memory. A
 * success lasts the positive lifetime, or until the JWT it came from expires
 * if that is sooner. A failure lasts the negative lifetime: short, so that a
 * transient failure recovers soon, and still long enough that a repeated guess
 * does not cost an Argon2 verification each time. When the cache is full, the
 * outcome used least recently goes.
 */
public final class AuthenticationCache {

    private static final String ERROR_MAX_SIZE_NOT_POSITIVE = "The maximum number of cached authentication outcomes must be positive, got %d.";
    private static final String ERROR_SHA_256_UNAVAILABLE   = "SHA-256 is not available.";

    private final Cache<String, Outcome> cache;

    /**
     * What authentication found for credentials.
     */
    public sealed interface Outcome {

        /**
         * The credentials are valid.
         *
         * @param pdpId the tenant the credentials belong to
         * @param expiresAt when the credentials expire, for a JWT, or null
         */
        record Success(String pdpId, @Nullable Instant expiresAt) implements Outcome {}

        /**
         * The credentials are not valid.
         *
         * @param message why, as the caller tells it
         */
        record Failure(String message) implements Outcome {}
    }

    /**
     * @param positiveTtl how long a success lasts at most
     * @param negativeTtl how long a failure lasts
     * @param maxSize how many outcomes the cache holds at most
     * @throws IllegalArgumentException if the maximum size is not positive
     */
    public AuthenticationCache(Duration positiveTtl, Duration negativeTtl, long maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException(ERROR_MAX_SIZE_NOT_POSITIVE.formatted(maxSize));
        }
        this.cache = Caffeine.newBuilder().maximumSize(maxSize).expireAfter(new TtlExpiry(positiveTtl, negativeTtl))
                .build();
    }

    /**
     * The outcome of credentials: the cached one, or the one the verification
     * finds, which is then cached.
     *
     * @param transport the transport that presents the credentials
     * @param credentials the credentials as presented
     * @param verification finds the outcome of the credentials; it must not
     * throw
     * @return the outcome
     */
    public Outcome outcomeOf(String transport, byte[] credentials, Supplier<Outcome> verification) {
        return cache.get(keyOf(transport, credentials), key -> verification.get());
    }

    private static String keyOf(String transport, byte[] credentials) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            digest.update(transport.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(credentials));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ERROR_SHA_256_UNAVAILABLE, e);
        }
    }

    /**
     * The lifetimes of outcomes.
     */
    static final class TtlExpiry implements Expiry<String, Outcome> {

        private final long positiveNanos;
        private final long negativeNanos;

        TtlExpiry(Duration positive, Duration negative) {
            this.positiveNanos = positive.toNanos();
            this.negativeNanos = negative.toNanos();
        }

        @Override
        public long expireAfterCreate(@NonNull String key, @NonNull Outcome value, long currentTime) {
            return ttl(value);
        }

        @Override
        public long expireAfterUpdate(@NonNull String key, @NonNull Outcome value, long currentTime,
                long currentDuration) {
            return ttl(value);
        }

        @Override
        public long expireAfterRead(@NonNull String key, @NonNull Outcome value, long currentTime,
                long currentDuration) {
            return currentDuration;
        }

        private long ttl(Outcome value) {
            return switch (value) {
            case Outcome.Failure ignored                              -> negativeNanos;
            case Outcome.Success(var pdpId, var exp) when exp != null -> ttlUntil(exp);
            case Outcome.Success ignored                              -> positiveNanos;
            };
        }

        // Clamp to [0, positiveNanos] so a far-future exp does not overflow
        // Duration.toNanos().
        private long ttlUntil(Instant exp) {
            val remaining = Duration.between(Instant.now(), exp);
            if (remaining.isNegative()) {
                return 0L;
            }
            if (remaining.compareTo(Duration.ofNanos(positiveNanos)) >= 0) {
                return positiveNanos;
            }
            return remaining.toNanos();
        }
    }
}
