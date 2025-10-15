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

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.val;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;
import java.util.stream.Stream;

import static io.sapl.functions.util.crypto.CryptoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class PemUtilsTest {

    private static final String SAMPLE_BASE64 = "aGVsbG8gd29ybGQ=";
    private static final byte[] SAMPLE_BYTES  = "hello world".getBytes();

    @BeforeAll
    static void setupBouncyCastle() {
        Security.addProvider(new BouncyCastleProvider());
    }

    /* stripPemHeaders Tests */

    @Test
    void stripPemHeaders_withPublicKeyHeaders_removesHeaders() {
        val pemContent = PEM_PUBLIC_KEY_BEGIN + '\n' + SAMPLE_BASE64 + '\n' + PEM_PUBLIC_KEY_END;

        val result = PemUtils.stripPemHeaders(pemContent, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    @Test
    void stripPemHeaders_withCertificateHeaders_removesHeaders() {
        val pemContent = PEM_CERTIFICATE_BEGIN + '\n' + SAMPLE_BASE64 + '\n' + PEM_CERTIFICATE_END;

        val result = PemUtils.stripPemHeaders(pemContent, PEM_CERTIFICATE_BEGIN, PEM_CERTIFICATE_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    @Test
    void stripPemHeaders_withWhitespace_removesWhitespace() {
        val pemContent = PEM_PUBLIC_KEY_BEGIN + "\n  " + SAMPLE_BASE64 + "  \n" + PEM_PUBLIC_KEY_END;

        val result = PemUtils.stripPemHeaders(pemContent, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    @Test
    void stripPemHeaders_withMultilineBase64_removesNewlines() {
        val multilineBase64 = "aGVsbG8g\nd29ybGQ=";
        val pemContent      = PEM_PUBLIC_KEY_BEGIN + '\n' + multilineBase64 + '\n' + PEM_PUBLIC_KEY_END;

        val result = PemUtils.stripPemHeaders(pemContent, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    @Test
    void stripPemHeaders_withOnlyBase64_leavesContentUnchanged() {
        val result = PemUtils.stripPemHeaders(SAMPLE_BASE64, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\n", "\r\n", " ", "\t", "  \n  " })
    void stripPemHeaders_removesVariousWhitespace(String whitespace) {
        val pemContent = PEM_PUBLIC_KEY_BEGIN + whitespace + SAMPLE_BASE64 + whitespace + PEM_PUBLIC_KEY_END;

        val result = PemUtils.stripPemHeaders(pemContent, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        assertEquals(SAMPLE_BASE64, result);
    }

    /* decodePublicKeyPem Tests */

    @Test
    void decodePublicKeyPem_withValidPem_decodesSuccessfully() {
        val keyPair = generateRsaKeyPair();
        val encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        val pemKey  = PEM_PUBLIC_KEY_BEGIN + '\n' + encoded + '\n' + PEM_PUBLIC_KEY_END;

        val result = PemUtils.decodePublicKeyPem(pemKey);

        assertNotNull(result);
        assertArrayEquals(keyPair.getPublic().getEncoded(), result);
    }

    @Test
    void decodePublicKeyPem_withMultilineBase64_decodesSuccessfully() {
        val keyPair   = generateRsaKeyPair();
        val encoded   = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        val multiline = insertNewlinesEvery64Chars(encoded);
        val pemKey    = PEM_PUBLIC_KEY_BEGIN + '\n' + multiline + '\n' + PEM_PUBLIC_KEY_END;

        val result = PemUtils.decodePublicKeyPem(pemKey);

        assertNotNull(result);
        assertArrayEquals(keyPair.getPublic().getEncoded(), result);
    }

    @Test
    void decodePublicKeyPem_withExtraWhitespace_decodesSuccessfully() {
        val keyPair = generateRsaKeyPair();
        val encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        val pemKey  = "  " + PEM_PUBLIC_KEY_BEGIN + "  \n  " + encoded + "  \n  " + PEM_PUBLIC_KEY_END + "  ";

        val result = PemUtils.decodePublicKeyPem(pemKey);

        assertNotNull(result);
        assertArrayEquals(keyPair.getPublic().getEncoded(), result);
    }

    @ParameterizedTest
    @MethodSource("provideKeyPairsForDecoding")
    void decodePublicKeyPem_withDifferentKeyTypes_decodesSuccessfully(KeyPair keyPair) {
        val encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        val pemKey  = PEM_PUBLIC_KEY_BEGIN + '\n' + encoded + '\n' + PEM_PUBLIC_KEY_END;

        val result = PemUtils.decodePublicKeyPem(pemKey);

        assertNotNull(result);
        assertArrayEquals(keyPair.getPublic().getEncoded(), result);
    }

    @Test
    void decodePublicKeyPem_withInvalidBase64_throwsException() {
        val invalidPem = PEM_PUBLIC_KEY_BEGIN + '\n' + "INVALID@#$BASE64" + '\n' + PEM_PUBLIC_KEY_END;

        val exception = assertThrows(PolicyEvaluationException.class, () -> PemUtils.decodePublicKeyPem(invalidPem));

        assertTrue(exception.getMessage().contains("Invalid Base64"));
        assertTrue(exception.getMessage().contains("public key"));
    }

    @Test
    void decodePublicKeyPem_withEmptyContent_throwsException() {
        val emptyPem = PEM_PUBLIC_KEY_BEGIN + '\n' + "!" + '\n' + PEM_PUBLIC_KEY_END;

        assertThrows(PolicyEvaluationException.class, () -> PemUtils.decodePublicKeyPem(emptyPem));
    }

    /* decodeCertificatePem Tests */

    @Test
    void decodeCertificatePem_withPemFormat_decodesSuccessfully() {
        val pemCertificate = PEM_CERTIFICATE_BEGIN + '\n' + SAMPLE_BASE64 + '\n' + PEM_CERTIFICATE_END;

        val result = PemUtils.decodeCertificatePem(pemCertificate);

        assertNotNull(result);
        assertArrayEquals(SAMPLE_BYTES, result);
    }

    @Test
    void decodeCertificatePem_withRawBase64_decodesSuccessfully() {
        val result = PemUtils.decodeCertificatePem(SAMPLE_BASE64);

        assertNotNull(result);
        assertArrayEquals(SAMPLE_BYTES, result);
    }

    @Test
    void decodeCertificatePem_withMultilineBase64_decodesSuccessfully() {
        val multiline      = "aGVsbG8g\nd29ybGQ=";
        val pemCertificate = PEM_CERTIFICATE_BEGIN + '\n' + multiline + '\n' + PEM_CERTIFICATE_END;

        val result = PemUtils.decodeCertificatePem(pemCertificate);

        assertNotNull(result);
        assertArrayEquals(SAMPLE_BYTES, result);
    }

    @Test
    void decodeCertificatePem_withExtraWhitespace_decodesSuccessfully() {
        val pemCertificate = "  " + PEM_CERTIFICATE_BEGIN + "  \n  " + SAMPLE_BASE64 + "  \n  " + PEM_CERTIFICATE_END
                + "  ";

        val result = PemUtils.decodeCertificatePem(pemCertificate);

        assertNotNull(result);
        assertArrayEquals(SAMPLE_BYTES, result);
    }

    @Test
    void decodeCertificatePem_withPartialMarker_decodesAsBase64() {
        val contentWithPartial = "BEGIN CERTIFICATE aGVsbG8gd29ybGQ=";

        val result = PemUtils.decodeCertificatePem(contentWithPartial);

        assertNotNull(result);
    }

    @Test
    void decodeCertificatePem_withInvalidBase64InPem_throwsException() {
        val invalidPem = PEM_CERTIFICATE_BEGIN + '\n' + "INVALID@#$" + '\n' + PEM_CERTIFICATE_END;

        val exception = assertThrows(PolicyEvaluationException.class, () -> PemUtils.decodeCertificatePem(invalidPem));

        assertTrue(exception.getMessage().contains("Invalid Base64"));
        assertTrue(exception.getMessage().contains("certificate"));
    }

    @Test
    void decodeCertificatePem_withInvalidBase64Raw_throwsException() {
        val exception = assertThrows(PolicyEvaluationException.class,
                () -> PemUtils.decodeCertificatePem("INVALID@#$BASE64"));

        assertTrue(exception.getMessage().contains("Invalid Base64"));
    }

    /* encodePublicKeyPem Tests */

    @Test
    void encodePublicKeyPem_withValidBytes_returnsValidPem() {
        val result = PemUtils.encodePublicKeyPem(SAMPLE_BYTES);

        assertNotNull(result);
        assertTrue(result.startsWith(PEM_PUBLIC_KEY_BEGIN));
        assertTrue(result.endsWith(PEM_PUBLIC_KEY_END));
        assertTrue(result.contains(SAMPLE_BASE64));
    }

    @Test
    void encodePublicKeyPem_withEmptyBytes_returnsValidPem() {
        val result = PemUtils.encodePublicKeyPem(new byte[0]);

        assertNotNull(result);
        assertTrue(result.startsWith(PEM_PUBLIC_KEY_BEGIN));
        assertTrue(result.endsWith(PEM_PUBLIC_KEY_END));
    }

    @ParameterizedTest
    @MethodSource("provideKeyPairsForEncoding")
    void encodePublicKeyPem_withDifferentKeyTypes_returnsValidPem(KeyPair keyPair) {
        val result = PemUtils.encodePublicKeyPem(keyPair.getPublic().getEncoded());

        assertNotNull(result);
        assertTrue(result.startsWith(PEM_PUBLIC_KEY_BEGIN));
        assertTrue(result.endsWith(PEM_PUBLIC_KEY_END));
        assertTrue(result.contains("\n"));
    }

    @Test
    void encodePublicKeyPem_hasCorrectFormat() {
        val result = PemUtils.encodePublicKeyPem(SAMPLE_BYTES);

        val lines = result.split("\n");
        assertEquals(PEM_PUBLIC_KEY_BEGIN, lines[0]);
        assertEquals(PEM_PUBLIC_KEY_END, lines[2]);
        assertEquals(SAMPLE_BASE64, lines[1]);
    }

    /* Round-trip Tests */

    @Test
    void roundTrip_encodeAndDecodePublicKey_preservesContent() {
        val keyPair  = generateRsaKeyPair();
        val original = keyPair.getPublic().getEncoded();

        val encoded = PemUtils.encodePublicKeyPem(original);
        val decoded = PemUtils.decodePublicKeyPem(encoded);

        assertArrayEquals(original, decoded);
    }

    @ParameterizedTest
    @MethodSource("provideKeyPairsForRoundTrip")
    void roundTrip_withDifferentKeyTypes_preservesContent(KeyPair keyPair) {
        val original = keyPair.getPublic().getEncoded();

        val encoded = PemUtils.encodePublicKeyPem(original);
        val decoded = PemUtils.decodePublicKeyPem(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    void roundTrip_decodeCertificateWithPemFormat_preservesContent() {
        val original = SAMPLE_BYTES;
        val base64   = Base64.getEncoder().encodeToString(original);
        val pemCert  = PEM_CERTIFICATE_BEGIN + '\n' + base64 + '\n' + PEM_CERTIFICATE_END;

        val decoded = PemUtils.decodeCertificatePem(pemCert);

        assertArrayEquals(original, decoded);
    }

    @Test
    void roundTrip_decodeCertificateWithRawFormat_preservesContent() {
        val original = SAMPLE_BYTES;
        val base64   = Base64.getEncoder().encodeToString(original);

        val decoded = PemUtils.decodeCertificatePem(base64);

        assertArrayEquals(original, decoded);
    }

    /* Test Data Providers */

    private static Stream<Arguments> provideKeyPairsForDecoding() {
        return Stream.of(Arguments.of(generateRsaKeyPair()), Arguments.of(generateEcKeyPair()),
                Arguments.of(generateEd25519KeyPair()));
    }

    private static Stream<Arguments> provideKeyPairsForEncoding() {
        return Stream.of(Arguments.of(generateRsaKeyPair()), Arguments.of(generateEcKeyPair()),
                Arguments.of(generateEd25519KeyPair()));
    }

    private static Stream<Arguments> provideKeyPairsForRoundTrip() {
        return Stream.of(Arguments.of(generateRsaKeyPair()), Arguments.of(generateEcKeyPair()),
                Arguments.of(generateEd25519KeyPair()));
    }

    /* Test Helper Methods */

    private static KeyPair generateRsaKeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_RSA);
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate RSA key pair", exception);
        }
    }

    private static KeyPair generateEcKeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EC);
            keyPairGenerator.initialize(256);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate EC key pair", exception);
        }
    }

    private static KeyPair generateEd25519KeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EDDSA);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate Ed25519 key pair", exception);
        }
    }

    private static String insertNewlinesEvery64Chars(String input) {
        val result = new StringBuilder();
        for (var i = 0; i < input.length(); i += 64) {
            result.append(input, i, Math.min(i + 64, input.length()));
            if (i + 64 < input.length()) {
                result.append('\n');
            }
        }
        return result.toString();
    }
}
