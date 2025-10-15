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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureFunctionLibraryTests {

    private static KeyPair      rsaKeyPair;
    private static KeyPair      ecP256KeyPair;
    private static KeyPair      ecP384KeyPair;
    private static KeyPair      ed25519KeyPair;
    private static final String TEST_MESSAGE = "test message for signing";

    @BeforeAll
    static void setupKeys() throws Exception {
        var rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        rsaKeyPair = rsaGenerator.generateKeyPair();

        var ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(256);
        ecP256KeyPair = ecGenerator.generateKeyPair();

        ecGenerator.initialize(384);
        ecP384KeyPair = ecGenerator.generateKeyPair();

        var edGenerator = KeyPairGenerator.getInstance("Ed25519");
        ed25519KeyPair = edGenerator.generateKeyPair();
    }

    /* RSA-SHA256 Tests */

    @Test
    void verifyRsaSha256_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha256(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyRsaSha256_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha256(Val.of("different message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    @Test
    void verifyRsaSha256_whenWrongKey_returnsFalse() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA");
        var signatureHex = HexFormat.of().formatHex(signature);

        var wrongKeyPair = KeyPairGenerator.getInstance("RSA");
        wrongKeyPair.initialize(2048);
        var wrongPublicKey = toPem(wrongKeyPair.generateKeyPair().getPublic());

        var result = SignatureFunctionLibrary.verifyRsaSha256(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(wrongPublicKey));

        assertFalse(result.getBoolean());
    }

    @Test
    void verifyRsaSha256_whenBase64Signature_returnsTrue() throws Exception {
        var signature       = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA");
        var publicKeyPem    = toPem(rsaKeyPair.getPublic());
        var signatureBase64 = Base64.getEncoder().encodeToString(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha256(Val.of(TEST_MESSAGE), Val.of(signatureBase64),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    /* RSA-SHA384 Tests */

    @Test
    void verifyRsaSha384_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA384withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha384(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyRsaSha384_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA384withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha384(Val.of("tampered message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    /* RSA-SHA512 Tests */

    @Test
    void verifyRsaSha512_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA512withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha512(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyRsaSha512_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA512withRSA");
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyRsaSha512(Val.of("modified message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    /* ECDSA P-256 Tests */

    @Test
    void verifyEcdsaP256_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(ecP256KeyPair.getPrivate(), "SHA256withECDSA");
        var publicKeyPem = toPem(ecP256KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEcdsaP256(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyEcdsaP256_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(ecP256KeyPair.getPrivate(), "SHA256withECDSA");
        var publicKeyPem = toPem(ecP256KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEcdsaP256(Val.of("altered message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    /* ECDSA P-384 Tests */

    @Test
    void verifyEcdsaP384_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(ecP384KeyPair.getPrivate(), "SHA384withECDSA");
        var publicKeyPem = toPem(ecP384KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEcdsaP384(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyEcdsaP384_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(ecP384KeyPair.getPrivate(), "SHA384withECDSA");
        var publicKeyPem = toPem(ecP384KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEcdsaP384(Val.of("changed message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    /* Ed25519 Tests */

    @Test
    void verifyEd25519_whenValidSignature_returnsTrue() throws Exception {
        var signature    = createSignature(ed25519KeyPair.getPrivate(), "Ed25519");
        var publicKeyPem = toPem(ed25519KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEd25519(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertTrue(result.getBoolean());
    }

    @Test
    void verifyEd25519_whenInvalidSignature_returnsFalse() throws Exception {
        var signature    = createSignature(ed25519KeyPair.getPrivate(), "Ed25519");
        var publicKeyPem = toPem(ed25519KeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEd25519(Val.of("different message"), Val.of(signatureHex),
                Val.of(publicKeyPem));

        assertFalse(result.getBoolean());
    }

    @Test
    void verifyEd25519_whenWrongKey_returnsFalse() throws Exception {
        var signature    = createSignature(ed25519KeyPair.getPrivate(), "Ed25519");
        var signatureHex = HexFormat.of().formatHex(signature);

        var wrongKeyPair   = KeyPairGenerator.getInstance("Ed25519");
        var wrongPublicKey = toPem(wrongKeyPair.generateKeyPair().getPublic());

        var result = SignatureFunctionLibrary.verifyEd25519(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(wrongPublicKey));

        assertFalse(result.getBoolean());
    }

    /* Error Handling Tests */

    @Test
    void verifyRsaSha256_whenInvalidPemFormat_returnsError() {
        var result = SignatureFunctionLibrary.verifyRsaSha256(Val.of(TEST_MESSAGE), Val.of("abc123"),
                Val.of("invalid pem"));
        assertTrue(result.isError());
    }

    @Test
    void verifyRsaSha256_whenInvalidSignatureFormat_returnsError() {
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var result       = SignatureFunctionLibrary.verifyRsaSha256(Val.of(TEST_MESSAGE),
                Val.of("not-hex-or-base64!@#$"), Val.of(publicKeyPem));
        assertTrue(result.isError());
    }

    @Test
    void verifyEcdsaP256_whenRsaKeyProvided_returnsError() throws Exception {
        var signature    = createSignature(ecP256KeyPair.getPrivate(), "SHA256withECDSA");
        var rsaPublicKey = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.verifyEcdsaP256(Val.of(TEST_MESSAGE), Val.of(signatureHex),
                Val.of(rsaPublicKey));

        assertTrue(result.isError());
    }

    /* Helper Methods */

    private byte[] createSignature(PrivateKey privateKey, String algorithm) throws Exception {
        var signature = Signature.getInstance(algorithm);
        signature.initSign(privateKey);
        signature.update(SignatureFunctionLibraryTests.TEST_MESSAGE.getBytes());
        return signature.sign();
    }

    private String toPem(PublicKey publicKey) {
        var encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
