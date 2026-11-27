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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.node.auth.AuthenticationCache.Outcome;
import io.sapl.node.auth.AuthenticationCache.TtlExpiry;
import lombok.val;

@DisplayName("AuthenticationCache")
class AuthenticationCacheTests {

    private static final Duration POSITIVE = Duration.ofMinutes(5);
    private static final Duration NEGATIVE = Duration.ofSeconds(5);

    private static final byte[] CREDENTIALS = "Basic Y3VsdGlzdDpzZWNyZXQ=".getBytes(StandardCharsets.UTF_8);

    @Nested
    @DisplayName("outcomes")
    class OutcomeTests {

        @Test
        @DisplayName("repeated credentials are verified once while their outcome lasts, a success and a failure alike")
        void whenCredentialsRepeatThenVerifiedOnce() {
            val cache         = new AuthenticationCache(POSITIVE, NEGATIVE, 100L);
            val verifications = new AtomicInteger();
            val success       = new Outcome.Success("tenant", null);
            val failure       = new Outcome.Failure("Authentication failed.");
            val otherBytes    = "Bearer sapl_wrong".getBytes(StandardCharsets.UTF_8);

            val outcomes = List.of(cache.outcomeOf("http", CREDENTIALS, () -> counted(verifications, success)),
                    cache.outcomeOf("http", CREDENTIALS, () -> counted(verifications, success)),
                    cache.outcomeOf("http", otherBytes, () -> counted(verifications, failure)),
                    cache.outcomeOf("http", otherBytes, () -> counted(verifications, failure)));

            assertThat(outcomes).containsExactly(success, success, failure, failure);
            assertThat(verifications).hasValue(2);
        }

        @Test
        @DisplayName("the same bytes on another transport are verified on their own, as they mean other credentials")
        void whenSameBytesOnAnotherTransportThenVerifiedSeparately() {
            val cache         = new AuthenticationCache(POSITIVE, NEGATIVE, 100L);
            val verifications = new AtomicInteger();
            val success       = new Outcome.Success("tenant", null);

            cache.outcomeOf("http", CREDENTIALS, () -> counted(verifications, success));
            cache.outcomeOf("rsocket", CREDENTIALS, () -> counted(verifications, success));

            assertThat(verifications).hasValue(2);
        }

        @Test
        @DisplayName("a cache for a single outcome keeps it")
        void whenCacheHoldsOneOutcomeThenItIsKept() {
            val cache         = new AuthenticationCache(POSITIVE, NEGATIVE, 1L);
            val verifications = new AtomicInteger();
            val success       = new Outcome.Success("tenant", null);

            cache.outcomeOf("http", CREDENTIALS, () -> counted(verifications, success));

            assertThat(cache.outcomeOf("http", CREDENTIALS, () -> counted(verifications, success))).isEqualTo(success);
            assertThat(verifications).hasValue(1);
        }

        private static Outcome counted(AtomicInteger verifications, Outcome outcome) {
            verifications.incrementAndGet();
            return outcome;
        }
    }

    @Nested
    @DisplayName("TtlExpiry")
    class TtlExpiryTests {

        @Test
        @DisplayName("a success without expiry uses the configured positive TTL")
        void whenSuccessHasNoExpiryThenPositiveTtlApplies() {
            val expiry = new TtlExpiry(POSITIVE, NEGATIVE);

            assertThat(expiry.expireAfterCreate("k", new Outcome.Success("default", null), 0L))
                    .isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("a JWT success uses the token's exp when it is closer than the positive TTL")
        void whenJwtExpiresBeforePositiveTtlThenTtlIsShortened() {
            val expiry           = new TtlExpiry(POSITIVE, NEGATIVE);
            val thirtyOutFromNow = Instant.now().plusSeconds(30);

            val ttl = expiry.expireAfterCreate("k", new Outcome.Success("default", thirtyOutFromNow), 0L);

            assertThat(ttl).isLessThan(POSITIVE.toNanos()).isGreaterThan(Duration.ofSeconds(25).toNanos())
                    .isLessThanOrEqualTo(Duration.ofSeconds(30).toNanos());
        }

        @Test
        @DisplayName("a JWT success uses the positive TTL when the token's exp is further away")
        void whenJwtExpiresAfterPositiveTtlThenPositiveTtlApplies() {
            val expiry = new TtlExpiry(POSITIVE, NEGATIVE);
            val farOut = Instant.now().plus(Duration.ofHours(1));

            assertThat(expiry.expireAfterCreate("k", new Outcome.Success("default", farOut), 0L))
                    .isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("a JWT success with an already elapsed exp expires immediately")
        void whenJwtExpiryAlreadyPastThenTtlIsZero() {
            val expiry = new TtlExpiry(POSITIVE, NEGATIVE);
            val past   = Instant.now().minusSeconds(10);

            assertThat(expiry.expireAfterCreate("k", new Outcome.Success("default", past), 0L)).isZero();
        }

        @Test
        @DisplayName("a JWT success with an exp beyond the nanosecond range clamps to the positive TTL")
        void whenJwtExpiryBeyondNanoRangeThenPositiveTtlApplies() {
            val expiry    = new TtlExpiry(POSITIVE, NEGATIVE);
            val farBeyond = Instant.now().plus(Duration.ofDays(365L * 1000));

            assertThat(expiry.expireAfterCreate("k", new Outcome.Success("default", farBeyond), 0L))
                    .isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("a failure uses the configured negative TTL")
        void whenFailureThenNegativeTtlApplies() {
            val expiry = new TtlExpiry(POSITIVE, NEGATIVE);

            assertThat(expiry.expireAfterCreate("k", new Outcome.Failure("denied"), 0L)).isEqualTo(NEGATIVE.toNanos());
        }
    }
}
