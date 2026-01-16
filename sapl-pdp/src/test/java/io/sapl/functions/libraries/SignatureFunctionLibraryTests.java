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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SignatureFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(SignatureFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    private static KeyPair rsaKeyPair;
    private static KeyPair ecP256KeyPair;
    private static KeyPair ecP384KeyPair;
    private static KeyPair ecP521KeyPair;
    private static KeyPair ed25519KeyPair;

    private static final String NECRONOMICON_EXCERPT = "That is not dead which can eternal lie, and with strange aeons even death may die.";
    private static final String CULTIST_INVOCATION   = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn";
    private static final String FORBIDDEN_KNOWLEDGE  = "The Elder Things dwelt in their cities beneath the Antarctic ice, and their knowledge was vast.";
    private static final String RITUAL_INCANTATION   = "Yog-Sothoth knows the gate. Yog-Sothoth is the gate. Yog-Sothoth is the key and guardian of the gate.";
    private static final String DEEP_ONE_CHANT       = "From Y'ha-nthlei beneath the waves, the Deep Ones rise to claim their covenant.";
    private static final String SHOGGOTH_WARNING     = "Tekeli-li! Tekeli-li! The shoggoths break free from their ancient bondage.";
    private static final String AZATHOTH_PROPHECY    = "At the center of infinity, blind idiot Azathoth gnaws hungrily in chaos.";
    private static final String NYARLATHOTEP_RIDDLE  = "The Crawling Chaos walks among mortals, wearing a thousand masks of deception.";
    private static final String MISKATONIC_RECORDS   = "The Miskatonic University archives contain fragments too terrible for human comprehension.";
    private static final String DUNWICH_HORROR       = "The hills of Dunwich concealed secrets that should have remained forever buried.";

    private static final String CTHULHU_MULTILINGUAL = "Cthulhu 克苏鲁 Κθούλου 크툴루 クトゥルフ كثولو"; // Cthulhu in English,
                                                                                              // Chinese, Greek, Korean,
                                                                                              // Japanese, Arabic
    private static final String ELDER_SIGN_UNICODE   = "☆ The Elder Sign protects against cosmic horrors ☆";
    private static final String ARABIC_NECRONOMICON  = "الكتاب المحرم - كتاب الموتى الناطق"; // "The Forbidden Book -
                                                                                             // The Book of the Speaking
                                                                                             // Dead" (Al Azif - Arabic
                                                                                             // name for Necronomicon)
    private static final String CYRILLIC_DREAMLANDS  = "Сновидения Странника раскрывают врата иных миров"; // "The
                                                                                                           // Dreamer's
                                                                                                           // Visions
                                                                                                           // reveal
                                                                                                           // gates to
                                                                                                           // other
                                                                                                           // worlds"
                                                                                                           // (Russian)

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

        ecGenerator.initialize(521);
        ecP521KeyPair = ecGenerator.generateKeyPair();

        var edGenerator = KeyPairGenerator.getInstance("Ed25519");
        ed25519KeyPair = edGenerator.generateKeyPair();
    }

    /* Parameterized Valid Signature Tests */

    @ParameterizedTest(name = "[{index}] {0} with valid signature returns true")
    @MethodSource("validSignatureScenarios")
    void isValid_whenValidSignature_returnsTrue(String algorithmName, Function<SignatureParams, Value> verifyFunction,
            KeyPair keyPair, String javaAlgorithm, String testMessage) throws Exception {
        var signature    = createSignature(keyPair.getPrivate(), javaAlgorithm, testMessage);
        var publicKeyPem = toPem(keyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = verifyFunction.apply(new SignatureParams(testMessage, signatureHex, publicKeyPem));

        assertThat(result).as("Signature verification should succeed for %s", algorithmName).isEqualTo(Value.TRUE);
    }

    static Stream<Arguments> validSignatureScenarios() {
        return Stream.of(
                arguments("RSA-SHA256", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), rsaKeyPair,
                        "SHA256withRSA", NECRONOMICON_EXCERPT),
                arguments("RSA-SHA384", wrapVerify(SignatureFunctionLibrary::isValidRsaSha384), rsaKeyPair,
                        "SHA384withRSA", CULTIST_INVOCATION),
                arguments("RSA-SHA512", wrapVerify(SignatureFunctionLibrary::isValidRsaSha512), rsaKeyPair,
                        "SHA512withRSA", FORBIDDEN_KNOWLEDGE),
                arguments("ECDSA-P256", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256), ecP256KeyPair,
                        "SHA256withECDSA", RITUAL_INCANTATION),
                arguments("ECDSA-P384", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP384), ecP384KeyPair,
                        "SHA384withECDSA", DEEP_ONE_CHANT),
                arguments("ECDSA-P521", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP521), ecP521KeyPair,
                        "SHA512withECDSA", SHOGGOTH_WARNING),
                arguments("Ed25519", wrapVerify(SignatureFunctionLibrary::isValidEd25519), ed25519KeyPair, "Ed25519",
                        AZATHOTH_PROPHECY));
    }

    /* Parameterized Invalid Signature Tests */

    @ParameterizedTest(name = "[{index}] {0} with tampered message returns false")
    @MethodSource("invalidSignatureScenarios")
    void isValid_whenTamperedMessage_returnsFalse(String algorithmName, Function<SignatureParams, Value> verifyFunction,
            KeyPair keyPair, String javaAlgorithm, String originalMessage, String tamperedMessage) throws Exception {
        var signature    = createSignature(keyPair.getPrivate(), javaAlgorithm, originalMessage);
        var publicKeyPem = toPem(keyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = verifyFunction.apply(new SignatureParams(tamperedMessage, signatureHex, publicKeyPem));

        assertThat(result).as("Signature verification should fail for tampered message in %s", algorithmName)
                .isEqualTo(Value.FALSE);
    }

    static Stream<Arguments> invalidSignatureScenarios() {
        return Stream.of(
                arguments("RSA-SHA256", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), rsaKeyPair,
                        "SHA256withRSA", NECRONOMICON_EXCERPT, "That is ALIVE which can eternal lie"),
                arguments("RSA-SHA384", wrapVerify(SignatureFunctionLibrary::isValidRsaSha384), rsaKeyPair,
                        "SHA384withRSA", CULTIST_INVOCATION, "Ph'nglui CORRUPTED Cthulhu"),
                arguments("RSA-SHA512", wrapVerify(SignatureFunctionLibrary::isValidRsaSha512), rsaKeyPair,
                        "SHA512withRSA", FORBIDDEN_KNOWLEDGE, "The Elder Things NEVER dwelt"),
                arguments("ECDSA-P256", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256), ecP256KeyPair,
                        "SHA256withECDSA", RITUAL_INCANTATION, "Yog-Sothoth FORGOT the gate"),
                arguments("ECDSA-P384", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP384), ecP384KeyPair,
                        "SHA384withECDSA", DEEP_ONE_CHANT, "From ATLANTIS beneath the waves"),
                arguments("ECDSA-P521", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP521), ecP521KeyPair,
                        "SHA512withECDSA", SHOGGOTH_WARNING, "Tekeli-li MODIFIED"),
                arguments("Ed25519", wrapVerify(SignatureFunctionLibrary::isValidEd25519), ed25519KeyPair, "Ed25519",
                        AZATHOTH_PROPHECY, "At the center of infinity, SANE Azathoth"));
    }

    /* Parameterized Wrong Key Tests */

    @ParameterizedTest(name = "[{index}] {0} with wrong key returns false")
    @MethodSource("wrongKeyScenarios")
    void isValid_whenWrongPublicKey_returnsFalse(String algorithmName, Function<SignatureParams, Value> verifyFunction,
            KeyPair correctKeyPair, KeyPair wrongKeyPair, String javaAlgorithm) throws Exception {
        var signature      = createSignature(correctKeyPair.getPrivate(), javaAlgorithm, NYARLATHOTEP_RIDDLE);
        var wrongPublicKey = toPem(wrongKeyPair.getPublic());
        var signatureHex   = HexFormat.of().formatHex(signature);

        var result = verifyFunction.apply(new SignatureParams(NYARLATHOTEP_RIDDLE, signatureHex, wrongPublicKey));

        assertThat(result).as("Signature verification should fail with wrong public key for %s", algorithmName)
                .isEqualTo(Value.FALSE);
    }

    static Stream<Arguments> wrongKeyScenarios() throws Exception {
        // Generate fresh wrong keys for each algorithm
        var wrongRsaKeyPair = generateKeyPair("RSA", 2048);
        var wrongEcP256     = generateKeyPair("EC", 256);
        var wrongEcP384     = generateKeyPair("EC", 384);
        var wrongEcP521     = generateKeyPair("EC", 521);
        var wrongEd25519    = generateKeyPair("Ed25519", null);

        return Stream.of(
                arguments("RSA-SHA256", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), rsaKeyPair,
                        wrongRsaKeyPair, "SHA256withRSA"),
                arguments("ECDSA-P256", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256), ecP256KeyPair,
                        wrongEcP256, "SHA256withECDSA"),
                arguments("ECDSA-P384", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP384), ecP384KeyPair,
                        wrongEcP384, "SHA384withECDSA"),
                arguments("ECDSA-P521", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP521), ecP521KeyPair,
                        wrongEcP521, "SHA512withECDSA"),
                arguments("Ed25519", wrapVerify(SignatureFunctionLibrary::isValidEd25519), ed25519KeyPair, wrongEd25519,
                        "Ed25519"));
    }

    /* Parameterized Format Tests (Hex and Base64) */

    @ParameterizedTest(name = "[{index}] {0} with {1} encoding")
    @MethodSource("signatureFormatScenarios")
    void verify_withDifferentEncodings_succeeds(String algorithmName, String encodingType,
            Function<SignatureParams, Value> verifyFunction, KeyPair keyPair, String javaAlgorithm,
            java.util.function.Function<byte[], String> encoder) throws Exception {
        var signature        = createSignature(keyPair.getPrivate(), javaAlgorithm, MISKATONIC_RECORDS);
        var publicKeyPem     = toPem(keyPair.getPublic());
        var encodedSignature = encoder.apply(signature);

        var result = verifyFunction.apply(new SignatureParams(MISKATONIC_RECORDS, encodedSignature, publicKeyPem));

        assertThat(result).as("%s with %s encoding should verify successfully", algorithmName, encodingType)
                .isEqualTo(Value.TRUE);
    }

    static Stream<Arguments> signatureFormatScenarios() {
        return Stream.of(
                arguments("RSA-SHA256", "Hex", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), rsaKeyPair,
                        "SHA256withRSA", (java.util.function.Function<byte[], String>) HexFormat.of()::formatHex),
                arguments("RSA-SHA256", "Base64", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), rsaKeyPair,
                        "SHA256withRSA",
                        (java.util.function.Function<byte[], String>) Base64.getEncoder()::encodeToString),
                arguments("Ed25519", "Hex", wrapVerify(SignatureFunctionLibrary::isValidEd25519), ed25519KeyPair,
                        "Ed25519", (java.util.function.Function<byte[], String>) HexFormat.of()::formatHex),
                arguments("Ed25519", "Base64", wrapVerify(SignatureFunctionLibrary::isValidEd25519), ed25519KeyPair,
                        "Ed25519", (java.util.function.Function<byte[], String>) Base64.getEncoder()::encodeToString));
    }

    /* Edge Case Tests */

    @ParameterizedTest(name = "[{index}] Message: {0}")
    @ValueSource(strings = { "", // Empty message
            "X", // Single character
            NECRONOMICON_EXCERPT, // Standard message
            DUNWICH_HORROR + DUNWICH_HORROR + DUNWICH_HORROR + DUNWICH_HORROR + DUNWICH_HORROR, // Long message
            CTHULHU_MULTILINGUAL, // Unicode with multiple scripts
            ELDER_SIGN_UNICODE, // Special unicode characters
            ARABIC_NECRONOMICON, // Arabic script
            CYRILLIC_DREAMLANDS, // Cyrillic script
            "\n\t  " + SHOGGOTH_WARNING + " \r\n", // Whitespace padded
            "   ", // Only whitespace
            "\uD83D\uDC19 The King in Yellow \uD83D\uDC51", // Emojis (🐙 octopus, 👑 crown)
    })
    void verify_withVariousMessageFormats_handlesCorrectly(String message) throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", message);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(message),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).as("Should handle message: '%s'", message).isEqualTo(Value.TRUE);
    }

    @Test
    void verify_withVeryLongMessage_handlesCorrectly() throws Exception {
        var longMessage  = FORBIDDEN_KNOWLEDGE.repeat(10000); // ~700KB message
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", longMessage);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(longMessage),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void verify_withLeadingAndTrailingWhitespace_stripsAndSucceeds() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", RITUAL_INCANTATION);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        // Test with leading and trailing whitespace
        var signatureWithWhitespace = "  \t\n  " + signatureHex + "  \r\n  ";

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(RITUAL_INCANTATION),
                (TextValue) Value.of(signatureWithWhitespace), (TextValue) Value.of(publicKeyPem));

        assertThat(result).as("Signature with leading/trailing whitespace should verify after stripping")
                .isEqualTo(Value.TRUE);
    }

    @Test
    void verify_withWhitespaceInMiddle_returnsError() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", RITUAL_INCANTATION);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        // Insert whitespace in the middle - should fail parsing
        var invalidSignature = signatureHex.substring(0, 10) + "   " + signatureHex.substring(10);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(RITUAL_INCANTATION),
                (TextValue) Value.of(invalidSignature), (TextValue) Value.of(publicKeyPem));

        assertThat(result).as("Signature with whitespace in middle should return error").isInstanceOf(ErrorValue.class);
    }

    /* Error Handling Tests */

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("errorScenarios")
    void verify_withInvalidInput_returnsError(String scenarioName, Function<SignatureParams, Value> verifyFunction,
            String message, String signature, String publicKey) {
        var result = verifyFunction.apply(new SignatureParams(message, signature, publicKey));

        assertThat(result).as("%s should return error", scenarioName).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> errorScenarios() {
        var validKeyPem = toPem(rsaKeyPair.getPublic());

        return Stream.of(
                arguments("Invalid PEM format", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256),
                        NECRONOMICON_EXCERPT, "abc123", "invalid pem key"),
                arguments("Invalid signature format (not hex or base64)",
                        wrapVerify(SignatureFunctionLibrary::isValidRsaSha256), CULTIST_INVOCATION,
                        "not-hex-or-base64!@#$%^&*()", validKeyPem),
                arguments("Empty signature", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256),
                        FORBIDDEN_KNOWLEDGE, "", validKeyPem),
                arguments("Malformed Base64", wrapVerify(SignatureFunctionLibrary::isValidRsaSha256),
                        RITUAL_INCANTATION, "This is not base64!", validKeyPem),
                arguments("Wrong key algorithm (RSA key for ECDSA)",
                        wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256), DEEP_ONE_CHANT, "abc123", validKeyPem));
    }

    /* RFC Test Vectors */

    @Test
    void verify_withRfc8032Ed25519TestVector1_succeeds() {
        // RFC 8032 Test Vector 1 for Ed25519
        var message   = "";
        var publicKey = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n-----END PUBLIC KEY-----";
        var signature = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b";

        var result = SignatureFunctionLibrary.isValidEd25519((TextValue) Value.of(message),
                (TextValue) Value.of(signature), (TextValue) Value.of(publicKey));

        assertThat(result).as("RFC 8032 test vector 1 should verify").isEqualTo(Value.TRUE);
    }

    @Test
    void verify_withRfc8032Ed25519TestVectorModified_fails() {
        // RFC 8032 Test Vector 1 with tampered message
        var tamperedMessage = "tampered";
        var publicKey       = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=\n-----END PUBLIC KEY-----";
        var signature       = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b";

        var result = SignatureFunctionLibrary.isValidEd25519((TextValue) Value.of(tamperedMessage),
                (TextValue) Value.of(signature), (TextValue) Value.of(publicKey));

        assertThat(result).as("RFC test vector with tampered message should fail").isEqualTo(Value.FALSE);
    }

    /* Thread Safety / Concurrent Verification Tests */

    @RepeatedTest(100)
    void isValid_whenConcurrentAccess_remainsThreadSafe() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", AZATHOTH_PROPHECY);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(AZATHOTH_PROPHECY),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    /* Helper Methods and Classes */

    private static byte[] createSignature(PrivateKey privateKey, String algorithm, String message) throws Exception {
        var signature = Signature.getInstance(algorithm);
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static String toPem(PublicKey publicKey) {
        var encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    private static KeyPair generateKeyPair(String algorithm, Integer keySize) throws Exception {
        var generator = KeyPairGenerator.getInstance(algorithm);
        if (keySize != null) {
            generator.initialize(keySize);
        }
        return generator.generateKeyPair();
    }

    private static Function<SignatureParams, Value> wrapVerify(
            TriFunction<TextValue, TextValue, TextValue, Value> verifyFunction) {
        return params -> verifyFunction.apply((TextValue) Value.of(params.message),
                (TextValue) Value.of(params.signature), (TextValue) Value.of(params.publicKey));
    }

    private record SignatureParams(String message, String signature, String publicKey) {}

    @FunctionalInterface
    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}
