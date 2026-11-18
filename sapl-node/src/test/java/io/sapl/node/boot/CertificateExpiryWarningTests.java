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
package io.sapl.node.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.node.testing.TestCertificates;
import lombok.val;

/**
 * An operator learns two weeks ahead that a TLS certificate expires, and again
 * every day until it is renewed, so that clients never meet an expired
 * certificate by surprise.
 */
@DisplayName("CertificateExpiryWarning")
class CertificateExpiryWarningTests {

    private static final Instant NOW      = Instant.parse("2026-11-18T10:00:00Z");
    private static final char[]  PASSWORD = "changeit".toCharArray();

    @TempDir
    Path directory;

    static Stream<Arguments> expiries() {
        return Stream.of(arguments("more than two weeks ahead", Duration.ofDays(15), null), arguments(
                "exactly two weeks ahead", Duration.ofDays(14),
                "The TLS certificate of the HTTP API expires on 2026-12-02T10:00:00Z, in 14 days. Renew it before then."),
                arguments("one day ahead", Duration.ofDays(1).plusHours(3),
                        "The TLS certificate of the HTTP API expires on 2026-11-19T13:00:00Z, in 1 day. Renew it before then."),
                arguments("within the day", Duration.ofHours(5),
                        "The TLS certificate of the HTTP API expires on 2026-11-18T15:00:00Z, in less than a day. Renew it before then."),
                arguments("in the past", Duration.ofDays(-2),
                        "The TLS certificate of the HTTP API expired on 2026-11-16T10:00:00Z. Clients refuse it until it is renewed."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expiries")
    @DisplayName("a certificate warns from two weeks before it expires")
    void whenCertificateExpiresSoonThenWarningNamesApiDateAndTime(String description, Duration remaining,
            String expected) {
        assertThat(CertificateExpiryWarning.warning("HTTP", NOW.plus(remaining), NOW))
                .isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    @DisplayName("the certificate of a chain that expires first decides")
    void whenChainHoldsSeveralCertificatesThenTheEarliestExpiryDecides() throws Exception {
        val leaf         = TestCertificates.write(directory, "leaf", NOW.plus(Duration.ofDays(90)));
        val intermediate = TestCertificates.write(directory, "intermediate", NOW.plus(Duration.ofDays(10)));
        val keyStore     = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("served", leaf.key(), PASSWORD,
                new Certificate[] { leaf.certificate(), intermediate.certificate() });

        assertThat(CertificateExpiryWarning.earliestExpiry(keyStore, "served"))
                .hasValueSatisfying(expiry -> assertThat(Duration.between(NOW, expiry).toDays()).isEqualTo(10));
    }

    @Test
    @DisplayName("a key store without certificates has no expiry")
    void whenKeyStoreIsEmptyThenNoExpiry() throws Exception {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        assertThat(CertificateExpiryWarning.earliestExpiry(keyStore, null)).isEmpty();
    }

}
