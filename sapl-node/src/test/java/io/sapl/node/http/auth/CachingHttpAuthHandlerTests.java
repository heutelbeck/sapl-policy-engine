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
package io.sapl.node.http.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.UserLookupService;
import io.sapl.node.http.auth.CachingHttpAuthHandler.Outcome;
import io.sapl.node.http.auth.CachingHttpAuthHandler.TtlExpiry;
import io.sapl.node.http.auth.HttpAuthHandler.HttpAuthResult;
import lombok.val;

@DisplayName("CachingHttpAuthHandler")
@ExtendWith(MockitoExtension.class)
class CachingHttpAuthHandlerTests {

    @Mock
    private SaplNodeProperties properties;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects non-positive maxSize")
        void whenMaxSizeNotPositiveThenThrows() {
            assertThatThrownBy(() -> new CachingHttpAuthHandler(properties, userLookupService, passwordEncoder, null,
                    Duration.ofMinutes(5), Duration.ofSeconds(5), 0L)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxSize");
            assertThatThrownBy(() -> new CachingHttpAuthHandler(properties, userLookupService, passwordEncoder, null,
                    Duration.ofMinutes(5), Duration.ofSeconds(5), -1L)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxSize");
        }

        @Test
        @DisplayName("accepts positive maxSize")
        void whenMaxSizePositiveThenConstructs() {
            assertThatCode(() -> new CachingHttpAuthHandler(properties, userLookupService, passwordEncoder, null,
                    Duration.ofMinutes(5), Duration.ofSeconds(5), 100L)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("TtlExpiry")
    class TtlExpiryTests {

        private static final Duration POSITIVE = Duration.ofMinutes(5);
        private static final Duration NEGATIVE = Duration.ofSeconds(5);

        @Test
        @DisplayName("non-JWT success uses the configured positive TTL")
        void whenSuccessHasNoExpiryThenPositiveTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val outcome = new Outcome.Success(new HttpAuthResult("default"), null);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("JWT success uses the token's exp when it is closer than positive TTL")
        void whenJwtExpiresBeforePositiveTtlThenTtlIsShortened() {
            val expiry           = new TtlExpiry(POSITIVE, NEGATIVE);
            val thirtyOutFromNow = Instant.now().plusSeconds(30);
            val outcome          = new Outcome.Success(new HttpAuthResult("default"), thirtyOutFromNow);

            val ttl = expiry.expireAfterCreate("k", outcome, 0L);

            assertThat(ttl).isLessThan(POSITIVE.toNanos()).isGreaterThan(Duration.ofSeconds(25).toNanos())
                    .isLessThanOrEqualTo(Duration.ofSeconds(30).toNanos());
        }

        @Test
        @DisplayName("JWT success uses positive TTL when token exp is further away")
        void whenJwtExpiresAfterPositiveTtlThenPositiveTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val farOut  = Instant.now().plus(Duration.ofHours(1));
            val outcome = new Outcome.Success(new HttpAuthResult("default"), farOut);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("JWT success with an already-elapsed exp expires immediately")
        void whenJwtExpiryAlreadyPastThenTtlIsZero() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val past    = Instant.now().minusSeconds(10);
            val outcome = new Outcome.Success(new HttpAuthResult("default"), past);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isZero();
        }

        @Test
        @DisplayName("failure uses the configured negative TTL")
        void whenFailureThenNegativeTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val outcome = new Outcome.Failure("denied");

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(NEGATIVE.toNanos());
        }
    }
}
