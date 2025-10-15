/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.util.crypto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPairGenerator;
import java.security.Signature;

import static io.sapl.functions.util.crypto.CryptoConstants.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests that verify the algorithm constants are actually supported by the JDK.
 * This catches typos and ensures the constants work with the actual crypto
 * APIs.
 */
class CryptoConstantsTest {

    @ParameterizedTest
    @ValueSource(strings = { ALGORITHM_RSA, ALGORITHM_EC, ALGORITHM_EDDSA })
    void keyAlgorithm_isActuallySupportedByJdk(String algorithm) {
        assertDoesNotThrow(() -> KeyPairGenerator.getInstance(algorithm),
                "Key algorithm should be supported by JDK: " + algorithm);
    }

    @ParameterizedTest
    @ValueSource(strings = { ALGORITHM_RSA_SHA256, ALGORITHM_RSA_SHA384, ALGORITHM_RSA_SHA512, ALGORITHM_ECDSA_SHA256,
            ALGORITHM_ECDSA_SHA384, ALGORITHM_ECDSA_SHA512, ALGORITHM_ED25519 })
    void signatureAlgorithm_isActuallySupportedByJdk(String algorithm) {
        assertDoesNotThrow(() -> Signature.getInstance(algorithm),
                "Signature algorithm should be supported by JDK: " + algorithm);
    }

    @ParameterizedTest
    @ValueSource(strings = { CERTIFICATE_TYPE_X509 })
    void certificateType_isActuallySupportedByJdk(String certificateType) {
        assertDoesNotThrow(() -> java.security.cert.CertificateFactory.getInstance(certificateType),
                "Certificate type should be supported by JDK: " + certificateType);
    }
}
