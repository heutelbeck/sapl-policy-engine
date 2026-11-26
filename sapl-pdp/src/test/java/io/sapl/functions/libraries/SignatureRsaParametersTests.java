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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureRsaParametersTests {

    @ParameterizedTest(name = "{0} modulus bits and exponent {1} are allowed: {2}")
    @CsvSource({ "0, 65537, false", "2047, 65537, false", "2048, 65537, true", "2049, 65537, true",
            "16384, 65537, true", "16385, 65537, false", "2048, 0, false", "2048, 1, false", "2048, 2, false",
            "2048, 3, true", "2048, 4, false", "2048, 8589934590, false", "2048, 8589934591, true",
            "2048, 8589934592, false", "2048, 8589934593, false", "2048, 18446744073709551615, false" })
    void whenRsaVerificationParametersAreCheckedThenTheDocumentedProfileApplies(int modulusBits, String exponent,
            boolean expected) {
        assertThat(SignatureFunctionLibrary.allowsRsaVerification(modulusBits, new BigInteger(exponent)))
                .isEqualTo(expected);
    }
}
