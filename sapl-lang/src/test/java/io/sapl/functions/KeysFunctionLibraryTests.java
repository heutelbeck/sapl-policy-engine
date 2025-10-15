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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeysFunctionLibraryTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static String              rsaPublicKeyPem;
    private static String              ecPublicKeyPem;
    private static String              ed25519PublicKeyPem;
    private static String              certificatePem;

    @BeforeAll
    static void setup() throws Exception {
        var rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        KeyPair rsaKeyPair = rsaGenerator.generateKeyPair();
        rsaPublicKeyPem = toPem(rsaKeyPair.getPublic());

        var ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(256);
        KeyPair ecKeyPair = ecGenerator.generateKeyPair();
        ecPublicKeyPem = toPem(ecKeyPair.getPublic());

        var edGenerator = KeyPairGenerator.getInstance("Ed25519");
        var edKeyPair   = edGenerator.generateKeyPair();
        ed25519PublicKeyPem = toPem(edKeyPair.getPublic());

        var certificate = generateCertificate(rsaKeyPair);
        certificatePem = toCertPem(certificate);
    }

    /* Parse Public Key Tests */

    @Test
    void parsePublicKey_whenRsaKey_returnsKeyObject() {
        var result = KeysFunctionLibrary.parsePublicKey(Val.of(rsaPublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("RSA", result.get().get("algorithm").asText());
        assertEquals("X.509", result.get().get("format").asText());
        assertTrue(result.get().has("size"));
    }

    @Test
    void parsePublicKey_whenEcKey_returnsKeyObject() {
        var result = KeysFunctionLibrary.parsePublicKey(Val.of(ecPublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("EC", result.get().get("algorithm").asText());
        assertTrue(result.get().has("curve"));
    }

    @Test
    void parsePublicKey_whenEd25519Key_returnsKeyObject() {
        var result = KeysFunctionLibrary.parsePublicKey(Val.of(ed25519PublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("EdDSA", result.get().get("algorithm").asText());
    }

    @Test
    void parsePublicKey_whenInvalidPem_returnsError() {
        var result = KeysFunctionLibrary.parsePublicKey(Val.of("invalid key data"));
        assertTrue(result.isError());
    }

    /* Extract Public Key From Certificate Tests */

    @Test
    void extractPublicKeyFromCertificate_whenValidCert_returnsPemKey() {
        var result = KeysFunctionLibrary.extractPublicKeyFromCertificate(Val.of(certificatePem));
        assertTrue(result.isDefined());
        assertTrue(result.getText().contains("BEGIN PUBLIC KEY"));
        assertTrue(result.getText().contains("END PUBLIC KEY"));
    }

    @Test
    void extractPublicKeyFromCertificate_whenInvalidCert_returnsError() {
        var result = KeysFunctionLibrary.extractPublicKeyFromCertificate(Val.of("invalid cert"));
        assertTrue(result.isError());
    }

    @Test
    void extractPublicKeyFromCertificate_extractedKeyCanBeParsed() {
        var extractedKey = KeysFunctionLibrary.extractPublicKeyFromCertificate(Val.of(certificatePem));
        var parsedKey    = KeysFunctionLibrary.parsePublicKey(extractedKey);
        assertTrue(parsedKey.isDefined());
        assertEquals("RSA", parsedKey.get().get("algorithm").asText());
    }

    /* Extract Key Algorithm Tests */

    @Test
    void extractKeyAlgorithm_whenRsaKey_returnsRsa() {
        var result = KeysFunctionLibrary.extractKeyAlgorithm(Val.of(rsaPublicKeyPem));
        assertEquals("RSA", result.getText());
    }

    @Test
    void extractKeyAlgorithm_whenEcKey_returnsEc() {
        var result = KeysFunctionLibrary.extractKeyAlgorithm(Val.of(ecPublicKeyPem));
        assertEquals("EC", result.getText());
    }

    @Test
    void extractKeyAlgorithm_whenEd25519Key_returnsEdDsa() {
        var result = KeysFunctionLibrary.extractKeyAlgorithm(Val.of(ed25519PublicKeyPem));
        assertEquals("EdDSA", result.getText());
    }

    @Test
    void extractKeyAlgorithm_whenInvalidKey_returnsError() {
        var result = KeysFunctionLibrary.extractKeyAlgorithm(Val.of("invalid"));
        assertTrue(result.isError());
    }

    /* Extract Key Size Tests */

    @Test
    void extractKeySize_whenRsa2048_returns2048() {
        var result = KeysFunctionLibrary.extractKeySize(Val.of(rsaPublicKeyPem));
        assertEquals(2048, result.get().asInt());
    }

    @Test
    void extractKeySize_whenEcP256_returns256() {
        var result = KeysFunctionLibrary.extractKeySize(Val.of(ecPublicKeyPem));
        assertEquals(256, result.get().asInt());
    }

    @Test
    void extractKeySize_whenEd25519_returns256() {
        var result = KeysFunctionLibrary.extractKeySize(Val.of(ed25519PublicKeyPem));
        assertEquals(256, result.get().asInt());
    }

    @Test
    void extractKeySize_whenInvalidKey_returnsError() {
        var result = KeysFunctionLibrary.extractKeySize(Val.of("invalid"));
        assertTrue(result.isError());
    }

    /* Extract EC Curve Tests */

    @Test
    void extractEcCurve_whenEcKey_returnsCurveName() {
        var result = KeysFunctionLibrary.extractEcCurve(Val.of(ecPublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("secp256r1", result.getText());
    }

    @Test
    void extractEcCurve_whenRsaKey_returnsError() {
        var result = KeysFunctionLibrary.extractEcCurve(Val.of(rsaPublicKeyPem));
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("not an EC key"));
    }

    @Test
    void extractEcCurve_whenInvalidKey_returnsError() {
        var result = KeysFunctionLibrary.extractEcCurve(Val.of("invalid"));
        assertTrue(result.isError());
    }

    /* Public Key to JWK Tests */

    @Test
    void publicKeyToJwk_whenRsaKey_returnsJwk() {
        var result = KeysFunctionLibrary.publicKeyToJwk(Val.of(rsaPublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("RSA", result.get().get("kty").asText());
        assertTrue(result.get().has("n"));
        assertTrue(result.get().has("e"));
    }

    @Test
    void publicKeyToJwk_whenEcKey_returnsJwk() {
        var result = KeysFunctionLibrary.publicKeyToJwk(Val.of(ecPublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("EC", result.get().get("kty").asText());
        assertTrue(result.get().has("crv"));
        assertTrue(result.get().has("x"));
        assertTrue(result.get().has("y"));
    }

    @Test
    void publicKeyToJwk_whenEd25519Key_returnsJwk() {
        var result = KeysFunctionLibrary.publicKeyToJwk(Val.of(ed25519PublicKeyPem));
        assertTrue(result.isDefined());
        assertEquals("OKP", result.get().get("kty").asText());
        assertEquals("Ed25519", result.get().get("crv").asText());
    }

    @Test
    void publicKeyToJwk_whenInvalidKey_returnsError() {
        var result = KeysFunctionLibrary.publicKeyToJwk(Val.of("invalid"));
        assertTrue(result.isError());
    }

    /* JWK to Public Key Tests */

    @Test
    void jwkToPublicKey_whenRsaJwk_returnsPem() {
        var jwk = KeysFunctionLibrary.publicKeyToJwk(Val.of(rsaPublicKeyPem));
        var pem = KeysFunctionLibrary.jwkToPublicKey(jwk);
        assertTrue(pem.isDefined());
        assertTrue(pem.getText().contains("BEGIN PUBLIC KEY"));
    }

    @Test
    void jwkToPublicKey_whenInvalidJwk_returnsError() {
        var invalidJwk = Val.of(JSON.objectNode().put("kty", "INVALID"));
        var result     = KeysFunctionLibrary.jwkToPublicKey(invalidJwk);
        assertTrue(result.isError());
    }

    @Test
    void jwkToPublicKey_whenMissingFields_returnsError() {
        var incompleteJwk = Val.of(JSON.objectNode().put("kty", "RSA"));
        var result        = KeysFunctionLibrary.jwkToPublicKey(incompleteJwk);
        assertTrue(result.isError());
    }

    /* Round-trip Tests */

    @Test
    void rsaKeyRoundTrip_pemToJwkToPem_maintainsKey() {
        var originalJwk  = KeysFunctionLibrary.publicKeyToJwk(Val.of(rsaPublicKeyPem));
        var convertedPem = KeysFunctionLibrary.jwkToPublicKey(originalJwk);
        var finalJwk     = KeysFunctionLibrary.publicKeyToJwk(convertedPem);

        assertEquals(originalJwk.get().get("n").asText(), finalJwk.get().get("n").asText());
        assertEquals(originalJwk.get().get("e").asText(), finalJwk.get().get("e").asText());
    }

    @Test
    void extractedKeyFromCert_canBeConvertedToJwk() {
        var extractedKey = KeysFunctionLibrary.extractPublicKeyFromCertificate(Val.of(certificatePem));
        var jwk          = KeysFunctionLibrary.publicKeyToJwk(extractedKey);
        assertTrue(jwk.isDefined());
        assertEquals("RSA", jwk.get().get("kty").asText());
    }

    /* Integration Tests */

    @Test
    void keySizeMatchesExpectedValue() {
        var keySize = KeysFunctionLibrary.extractKeySize(Val.of(rsaPublicKeyPem));
        assertEquals(2048, keySize.get().asInt());

        var algorithm = KeysFunctionLibrary.extractKeyAlgorithm(Val.of(rsaPublicKeyPem));
        assertEquals("RSA", algorithm.getText());
    }

    @Test
    void ecKeysProvideConsistentInformation() {
        var algorithm = KeysFunctionLibrary.extractKeyAlgorithm(Val.of(ecPublicKeyPem));
        var curve     = KeysFunctionLibrary.extractEcCurve(Val.of(ecPublicKeyPem));
        var size      = KeysFunctionLibrary.extractKeySize(Val.of(ecPublicKeyPem));

        assertEquals("EC", algorithm.getText());
        assertEquals("secp256r1", curve.getText());
        assertEquals(256, size.get().asInt());
    }

    @Test
    void allKeyTypes_canBeConvertedToJwk() {
        var rsaJwk = KeysFunctionLibrary.publicKeyToJwk(Val.of(rsaPublicKeyPem));
        assertTrue(rsaJwk.isDefined());
        assertEquals("RSA", rsaJwk.get().get("kty").asText());

        var ecJwk = KeysFunctionLibrary.publicKeyToJwk(Val.of(ecPublicKeyPem));
        assertTrue(ecJwk.isDefined());
        assertEquals("EC", ecJwk.get().get("kty").asText());

        var edJwk = KeysFunctionLibrary.publicKeyToJwk(Val.of(ed25519PublicKeyPem));
        assertTrue(edJwk.isDefined());
        assertEquals("OKP", edJwk.get().get("kty").asText());
    }

    /* Helper Methods */

    private static String toPem(PublicKey publicKey) {
        var encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    private static String toCertPem(X509Certificate certificate) throws Exception {
        var encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }

    private static X509Certificate generateCertificate(KeyPair keyPair) throws Exception {
        var now       = Instant.now();
        var notBefore = now.minus(1, ChronoUnit.DAYS);
        var notAfter  = now.plus(365, ChronoUnit.DAYS);

        var subject = new X500Name("CN=Test,O=Test Org,C=US");

        var certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(notBefore), Date.from(notAfter), subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var holder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
