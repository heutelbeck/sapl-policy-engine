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
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
@DisplayName("X.509 DNS name casing")
class X509FunctionLibraryLocaleTests {

    @ResourceLock(Resources.LOCALE)
    @CsvSource({ "www.example.com,true", "ADMIN.API.EXAMPLE.COM,true", "admin.api.example.com,true",
            "other.example.org,false" })
    @ParameterizedTest(name = "{0} matches: {1}")
    @DisplayName("DNS name matching is independent of the default locale")
    void whenDnsNamesAreMatchedThenTheDefaultLocaleDoesNotChangeTheResult(String dnsName, boolean expected)
            throws IOException {
        val certificate = certificate();
        val previous    = Locale.getDefault();
        val display     = Locale.getDefault(Locale.Category.DISPLAY);
        val format      = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(X509FunctionLibrary.hasDnsName(certificate, Value.of(dnsName))).isEqualTo(Value.of(expected));
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    private static TextValue certificate() throws IOException {
        try (val input = Objects
                .requireNonNull(X509FunctionLibraryLocaleTests.class.getResourceAsStream("/x509/san-locale.pem"))) {
            return Value.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
