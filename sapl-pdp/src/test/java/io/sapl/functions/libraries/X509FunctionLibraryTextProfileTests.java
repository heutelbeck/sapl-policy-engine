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
package io.sapl.functions.libraries;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("X.509 DNS and fingerprint text formats")
class X509FunctionLibraryTextProfileTests {

    @ParameterizedTest(name = "{1} matches: {2}")
    @DisplayName("DNS wildcards match one label without a trailing dot")
    @CsvSource({ "san-locale.pem,www.example.com,true", "san-locale.pem,v1.api.example.com,true",
            "san-locale.pem,api.example.com,false", "san-locale.pem,a.b.api.example.com,false",
            "san-locale.pem,www.example.com.,false", "wildcard-final-dot.pem,x.example.io.,false" })
    void whenDnsNamesAreMatchedThenWildcardsHaveOneLabelAndNoTrailingDot(String file, String name, boolean expected)
            throws IOException {
        assertThat(X509FunctionLibrary.hasDnsName(certificate(file), Value.of(name))).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "fingerprint {0} matches: {1}")
    @DisplayName("Fingerprint matching accepts hex case and colon separators")
    @CsvSource({ "40b108b61c5224830251af7bdf63d0ca29e7a772618bac8f82243c068f041608,true",
            "40B108B61C5224830251AF7BDF63D0CA29E7A772618BAC8F82243C068F041608,true",
            "40:b1:08:b6:1c:52:24:83:02:51:af:7b:df:63:d0:ca:29:e7:a7:72:61:8b:ac:8f:82:24:3c:06:8f:04:16:08,true",
            "0000000000000000000000000000000000000000000000000000000000000000,false" })
    void whenFingerprintsAreMatchedThenHexCaseAndColonSeparatorsAreAccepted(String fingerprint, boolean expected)
            throws IOException {
        assertThat(X509FunctionLibrary.matchesFingerprint(certificate("san-locale.pem"), Value.of(fingerprint),
                Value.of("SHA-256"))).isEqualTo(Value.of(expected));
    }

    private static TextValue certificate(String file) throws IOException {
        try (val input = Objects
                .requireNonNull(X509FunctionLibraryTextProfileTests.class.getResourceAsStream("/x509/" + file))) {
            return Value.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
