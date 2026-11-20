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
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SignatureFunctionLibrary")
class SignatureFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new SignatureFunctionLibrary())).doesNotThrowAnyException();
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

    private static final String RFC_ED25519_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a";
    private static final String RFC_ED25519_SIGNATURE  = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555f"
            + "b8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b";

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

    @ParameterizedTest(name = "[{index}] {0} with valid signature returns true")
    @MethodSource("validSignatureScenarios")
    void isValidWhenValidSignatureReturnsTrue(String algorithmName, Function<SignatureParams, Value> verifyFunction,
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

    @Test
    @DisplayName("isValidEcdsaP256 rejects a same-order different curve (secp256k1), not just the order bit length")
    void isValidEcdsaP256RejectsSameOrderDifferentCurve() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        var generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256k1"));
        var publicKeyPem = toPem(generator.generateKeyPair().getPublic());

        // The curve check runs before signature verification, so a dummy signature
        // suffices.
        var result = wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256)
                .apply(new SignatureParams(RITUAL_INCANTATION, "abcdef", publicKeyPem));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("elliptic curve");
    }

    @ParameterizedTest(name = "[{index}] {0} with tampered message returns false")
    @MethodSource("invalidSignatureScenarios")
    void isValidWhenTamperedMessageReturnsFalse(String algorithmName, Function<SignatureParams, Value> verifyFunction,
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

    @ParameterizedTest(name = "[{index}] {0} with wrong key returns false")
    @MethodSource("wrongKeyScenarios")
    void isValidWhenWrongPublicKeyReturnsFalse(String algorithmName, Function<SignatureParams, Value> verifyFunction,
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

    @ParameterizedTest(name = "[{index}] {0} with {1} encoding")
    @MethodSource("signatureFormatScenarios")
    void verifyWithDifferentEncodingsSucceeds(String algorithmName, String encodingType,
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
    void verifyWithVariousMessageFormatsHandlesCorrectly(String message) throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", message);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(message),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).as("Should handle message: '%s'", message).isEqualTo(Value.TRUE);
    }

    @Test
    void verifyWithVeryLongMessageHandlesCorrectly() throws Exception {
        var longMessage  = FORBIDDEN_KNOWLEDGE.repeat(10000); // ~700KB message
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", longMessage);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(longMessage),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void verifyWithLeadingAndTrailingWhitespaceStripsAndSucceeds() throws Exception {
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
    void verifyWithWhitespaceInMiddleReturnsError() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", RITUAL_INCANTATION);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        // Insert whitespace in the middle - should fail parsing
        var invalidSignature = signatureHex.substring(0, 10) + "   " + signatureHex.substring(10);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(RITUAL_INCANTATION),
                (TextValue) Value.of(invalidSignature), (TextValue) Value.of(publicKeyPem));

        assertThat(result).as("Signature with whitespace in middle should return error").isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("errorScenarios")
    void verifyWithInvalidInputReturnsError(String scenarioName, Function<SignatureParams, Value> verifyFunction,
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

    @RepeatedTest(100)
    void isValidRsaSha256WhenInvokedRepeatedlyRemainsConsistent() throws Exception {
        var signature    = createSignature(rsaKeyPair.getPrivate(), "SHA256withRSA", AZATHOTH_PROPHECY);
        var publicKeyPem = toPem(rsaKeyPair.getPublic());
        var signatureHex = HexFormat.of().formatHex(signature);

        var result = SignatureFunctionLibrary.isValidRsaSha256((TextValue) Value.of(AZATHOTH_PROPHECY),
                (TextValue) Value.of(signatureHex), (TextValue) Value.of(publicKeyPem));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Nested
    @DisplayName("Curve enforcement")
    class CurveEnforcement {

        @ParameterizedTest(name = "[{index}] {0} rejects a key on the wrong NIST curve")
        @MethodSource("io.sapl.functions.libraries.SignatureFunctionLibraryTests#wrongCurveScenarios")
        @DisplayName("ECDSA curve functions reject keys on a different NIST curve")
        void isValidEcdsaWhenKeyOnDifferentNistCurveReturnsError(String scenarioName,
                Function<SignatureParams, Value> verifyFunction, KeyPair wrongCurveKeyPair, String javaAlgorithm)
                throws Exception {
            var signature    = createSignature(wrongCurveKeyPair.getPrivate(), javaAlgorithm, RITUAL_INCANTATION);
            var publicKeyPem = toPem(wrongCurveKeyPair.getPublic());
            var signatureHex = HexFormat.of().formatHex(signature);

            var result = verifyFunction.apply(new SignatureParams(RITUAL_INCANTATION, signatureHex, publicKeyPem));

            assertThat(result).as("%s must not accept a key from another NIST curve", scenarioName)
                    .isInstanceOf(ErrorValue.class);
        }
    }

    static Stream<Arguments> wrongCurveScenarios() {
        return Stream.of(
                arguments("isValidEcdsaP256 with P-384 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256),
                        ecP384KeyPair, "SHA256withECDSA"),
                arguments("isValidEcdsaP256 with P-521 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP256),
                        ecP521KeyPair, "SHA256withECDSA"),
                arguments("isValidEcdsaP384 with P-256 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP384),
                        ecP256KeyPair, "SHA384withECDSA"),
                arguments("isValidEcdsaP384 with P-521 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP384),
                        ecP521KeyPair, "SHA384withECDSA"),
                arguments("isValidEcdsaP521 with P-256 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP521),
                        ecP256KeyPair, "SHA512withECDSA"),
                arguments("isValidEcdsaP521 with P-384 key", wrapVerify(SignatureFunctionLibrary::isValidEcdsaP521),
                        ecP384KeyPair, "SHA512withECDSA"));
    }

    @Nested
    @DisplayName("Strict Ed25519 verification")
    class StrictEd25519 {

        @ParameterizedTest(name = "{0}")
        @DisplayName("Invalid public keys produce error values")
        @MethodSource("io.sapl.functions.libraries.SignatureFunctionLibraryTests#invalidEd25519Keys")
        void whenPublicKeyIsInvalidThenVerificationReturnsError(String name, String point) throws IOException {
            val signature = "01" + "00".repeat(63);
            val result    = wrapVerify(SignatureFunctionLibrary::isValidEd25519)
                    .apply(new SignatureParams("An arbitrary message.", signature, ed25519PublicKey(point)));
            assertThat(result).as(name).isInstanceOf(ErrorValue.class);
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("Only valid canonical signatures are accepted")
        @MethodSource("io.sapl.functions.libraries.SignatureFunctionLibraryTests#ed25519Signatures")
        void whenSignatureIsCheckedThenStrictVerificationDeterminesValidity(String name, String message,
                String signature, Value expected) throws IOException {
            val result = wrapVerify(SignatureFunctionLibrary::isValidEd25519)
                    .apply(new SignatureParams(message, signature, ed25519PublicKey(RFC_ED25519_PUBLIC_KEY)));
            assertThat(result).as(name).isEqualTo(expected);
        }
    }

    static Stream<Arguments> invalidEd25519Keys() {
        return Stream.of(arguments("identity", "01" + "00".repeat(31)), arguments("order four", "00".repeat(32)),
                arguments("order two", "ec" + "ff".repeat(30) + "7f"),
                arguments("order eight", "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05"),
                arguments("mixed order", "95" + "99".repeat(31)),
                arguments("noncanonical identity", "ee" + "ff".repeat(30) + "7f"),
                arguments("identity with sign bit", "01" + "00".repeat(30) + "80"),
                arguments("not a point", "02" + "00".repeat(31)), arguments("one byte short", "00".repeat(31)),
                arguments("one byte long", "00".repeat(33)), arguments("empty", ""));
    }

    static Stream<Arguments> ed25519Signatures() {
        val r = RFC_ED25519_SIGNATURE.substring(0, 64);
        val s = RFC_ED25519_SIGNATURE.substring(64);
        return Stream.of(arguments("RFC 8032 empty message", "", RFC_ED25519_SIGNATURE, Value.TRUE),
                arguments("wrong message", "An arbitrary message.", RFC_ED25519_SIGNATURE, Value.FALSE),
                arguments("empty signature", "", "", Value.FALSE),
                arguments("one byte short", "", RFC_ED25519_SIGNATURE.substring(0, 126), Value.FALSE),
                arguments("one byte long", "", RFC_ED25519_SIGNATURE + "00", Value.FALSE),
                arguments("wrong signature", "", "00".repeat(64), Value.FALSE),
                arguments("identity R", "", "01" + "00".repeat(31) + s, Value.FALSE),
                arguments("mixed order R", "", "95" + "99".repeat(31) + s, Value.FALSE),
                arguments("noncanonical R", "", "ee" + "ff".repeat(30) + "7f" + s, Value.FALSE),
                arguments("R not a point", "", "02" + "00".repeat(31) + s, Value.FALSE),
                arguments("S equals group order", "",
                        r + "edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010", Value.FALSE),
                arguments("S above group order", "", r + "ff".repeat(32), Value.FALSE));
    }

    private static String ed25519PublicKey(String point) throws IOException {
        val info = new SubjectPublicKeyInfo(new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
                HexFormat.of().parseHex(point));
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(info.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

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
