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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import io.sapl.functions.util.crypto.CertificateUtils;
import io.sapl.functions.util.crypto.KeyUtils;
import io.sapl.functions.util.crypto.PemUtils;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import static io.sapl.functions.util.crypto.CryptoConstants.*;

/**
 * Provides functions for parsing and converting cryptographic key material.
 * Supports parsing public keys from PEM format, extracting keys from
 * certificates, and converting between different key representations.
 */
@UtilityClass
@FunctionLibrary(name = KeysFunctionLibrary.NAME, description = KeysFunctionLibrary.DESCRIPTION)
public class KeysFunctionLibrary {

    public static final String NAME        = "keys";
    public static final String DESCRIPTION = "Functions for parsing and converting cryptographic key material including PEM parsing and JWK conversion.";

    private static final String RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_OBJECT = """
            {
                "type": "object"
            }
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /* Key Parsing */

    @Function(docs = """
            ```parsePublicKey(TEXT keyPem)```: Parses a PEM-encoded public key.

            Accepts RSA, EC (Elliptic Curve), and EdDSA public keys in PEM format.
            Returns a structured object with key type, algorithm, and format information.

            **Examples:**
            ```sapl
            policy "parse key"
            permit
            where
              var key = keys.parsePublicKey(publicKeyPem);
              key.algorithm == "RSA";
              key.format == "X.509";
            ```
            """, schema = RETURNS_OBJECT)
    public Val parsePublicKey(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            val keyObject = buildKeyObject(publicKey);
            return Val.of(keyObject);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to parse public key: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractPublicKeyFromCertificate(TEXT certPem)```: Extracts the public key from an X.509 certificate.

            Parses the certificate and returns the embedded public key in PEM format.
            The returned key can be used with signature verification functions.

            **Examples:**
            ```sapl
            policy "extract key from cert"
            permit
            where
              var publicKey = keys.extractPublicKeyFromCertificate(clientCert);
              signature.verifyRsaSha256(message, sig, publicKey);
            ```
            """, schema = RETURNS_TEXT)
    public Val extractPublicKeyFromCertificate(@Text Val certPem) {
        try {
            val certificate = CertificateUtils.parseCertificate(certPem.getText());
            val publicKey   = certificate.getPublicKey();
            val keyPem      = PemUtils.encodePublicKeyPem(publicKey.getEncoded());
            return Val.of(keyPem);
        } catch (PolicyEvaluationException | CertificateException exception) {
            return Val.error("Failed to extract public key from certificate: " + exception.getMessage());
        }
    }

    /* Key Information Extraction */

    @Function(docs = """
            ```extractKeyAlgorithm(TEXT keyPem)```: Extracts the algorithm name from a public key.

            Returns the key algorithm as a string: "RSA", "EC", or "EdDSA".

            **Examples:**
            ```sapl
            policy "check key type"
            permit
            where
              keys.extractKeyAlgorithm(publicKey) == "RSA";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractKeyAlgorithm(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            return Val.of(publicKey.getAlgorithm());
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to extract key algorithm: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractKeySize(TEXT keyPem)```: Extracts the key size in bits.

            Returns the key size for RSA keys (e.g., 2048, 4096) or the curve size
            for EC keys (e.g., 256, 384). For EdDSA keys, returns the fixed key size.

            **Examples:**
            ```sapl
            policy "require strong keys"
            permit
            where
              keys.extractKeySize(publicKey) >= 2048;
            ```
            """, schema = RETURNS_NUMBER)
    public Val extractKeySize(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            val keySize   = KeyUtils.getKeySize(publicKey);
            return Val.of(keySize);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to extract key size: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractEcCurve(TEXT keyPem)```: Extracts the elliptic curve name from an EC public key.

            Returns the curve name for elliptic curve keys (e.g., "secp256r1", "secp384r1", "secp521r1").
            Returns an error if the key is not an EC key.

            **Examples:**
            ```sapl
            policy "require p256 curve"
            permit
            where
              keys.extractEcCurve(publicKey) == "secp256r1";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractEcCurve(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());

            if (!(publicKey instanceof ECPublicKey ecKey)) {
                return Val.error("Key is not an EC key");
            }

            val curveName = KeyUtils.extractEcCurveName(ecKey);
            return Val.of(curveName);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to extract EC curve: " + exception.getMessage());
        }
    }

    /* JWK Conversion */

    @Function(docs = """
            ```publicKeyToJwk(TEXT publicKeyPem)```: Converts a PEM public key to JWK format.

            Converts RSA, EC, or EdDSA public keys to JSON Web Key (JWK) format as defined
            in RFC 7517. The JWK includes the key type (kty), algorithm parameters, and
            key material.

            **Examples:**
            ```sapl
            policy "convert to jwk"
            permit
            where
              var jwk = keys.publicKeyToJwk(publicKeyPem);
              jwk.kty == "RSA";
              jwk.n != null;
            ```
            """, schema = RETURNS_OBJECT)
    public Val publicKeyToJwk(@Text Val publicKeyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(publicKeyPem.getText());
            val jwk       = convertPublicKeyToJwk(publicKey);
            return Val.of(jwk);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to convert key to JWK: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```jwkToPublicKey(OBJECT jwk)```: Converts a JWK to PEM public key format.

            Converts a JSON Web Key (JWK) object back to PEM format. Supports RSA, EC,
            and EdDSA keys. Returns the public key in standard PEM encoding.

            **Examples:**
            ```sapl
            policy "convert from jwk"
            permit
            where
              var publicKeyPem = keys.jwkToPublicKey(jwkObject);
              signature.verifyRsaSha256(message, sig, publicKeyPem);
            ```
            """, schema = RETURNS_TEXT)
    public Val jwkToPublicKey(@JsonObject Val jwk) {
        try {
            val publicKey = convertJwkToPublicKey(jwk.get());
            val keyPem    = PemUtils.encodePublicKeyPem(publicKey.getEncoded());
            return Val.of(keyPem);
        } catch (PolicyEvaluationException | NoSuchAlgorithmException | InvalidKeySpecException exception) {
            return Val.error("Failed to convert JWK to public key: " + exception.getMessage());
        }
    }

    /**
     * Builds a JSON object representation of a public key.
     */
    private static JsonNode buildKeyObject(PublicKey publicKey) {
        val keyObject = JSON.objectNode();
        keyObject.put("algorithm", publicKey.getAlgorithm());
        keyObject.put("format", publicKey.getFormat());
        keyObject.put("size", KeyUtils.getKeySize(publicKey));

        if (publicKey instanceof ECPublicKey ecKey) {
            keyObject.put("curve", KeyUtils.extractEcCurveName(ecKey));
        }

        return keyObject;
    }

    /**
     * Converts a PublicKey to JWK format.
     */
    private static JsonNode convertPublicKeyToJwk(PublicKey publicKey) {
        return switch (publicKey) {
        case RSAPublicKey rsaKey   -> {
            val jwk = JSON.objectNode();
            jwk.put("kty", JWK_KEY_TYPE_RSA);
            jwk.put("n", base64UrlEncode(rsaKey.getModulus()));
            jwk.put("e", base64UrlEncode(rsaKey.getPublicExponent()));
            yield jwk;
        }
        case ECPublicKey ecKey     -> {
            val jwk = JSON.objectNode();
            jwk.put("kty", JWK_KEY_TYPE_EC);
            jwk.put("crv", KeyUtils.getJwkCurveName(ecKey));
            jwk.put("x", base64UrlEncode(ecKey.getW().getAffineX()));
            jwk.put("y", base64UrlEncode(ecKey.getW().getAffineY()));
            yield jwk;
        }
        case EdECPublicKey edEcKey -> {
            val jwk = JSON.objectNode();
            jwk.put("kty", JWK_KEY_TYPE_OKP);
            jwk.put("crv", CURVE_ED25519);
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(edEcKey.getEncoded()));
            yield jwk;
        }
        default                    -> throw new PolicyEvaluationException(
                "Unsupported key type for JWK conversion: " + publicKey.getClass().getName());
        };
    }

    /**
     * Converts a JWK to PublicKey.
     */
    private static PublicKey convertJwkToPublicKey(JsonNode jwkNode)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        val keyTypeNode = jwkNode.get("kty");
        if (keyTypeNode == null || !keyTypeNode.isTextual()) {
            throw new PolicyEvaluationException("JWK missing required 'kty' field");
        }

        val keyType = keyTypeNode.asText();

        return switch (keyType) {
        case JWK_KEY_TYPE_RSA -> convertRsaJwkToPublicKey(jwkNode);
        case JWK_KEY_TYPE_EC  ->
            throw new PolicyEvaluationException("EC JWK to PublicKey conversion not yet implemented");
        case JWK_KEY_TYPE_OKP ->
            throw new PolicyEvaluationException("OKP JWK to PublicKey conversion not yet implemented");
        default               -> throw new PolicyEvaluationException("Unsupported JWK key type: " + keyType);
        };
    }

    /**
     * Converts an RSA JWK to PublicKey.
     */
    private static PublicKey convertRsaJwkToPublicKey(JsonNode jwkNode)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        val modulusNode  = jwkNode.get("n");
        val exponentNode = jwkNode.get("e");

        if (modulusNode == null || !modulusNode.isTextual()) {
            throw new PolicyEvaluationException("RSA JWK missing required 'n' (modulus) field");
        }
        if (exponentNode == null || !exponentNode.isTextual()) {
            throw new PolicyEvaluationException("RSA JWK missing required 'e' (exponent) field");
        }

        val modulusBytes  = base64UrlDecode(modulusNode.asText());
        val exponentBytes = base64UrlDecode(exponentNode.asText());
        val modulus       = new BigInteger(1, modulusBytes);
        val exponent      = new BigInteger(1, exponentBytes);

        val keySpec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance(ALGORITHM_RSA).generatePublic(keySpec);
    }

    /**
     * Base64 URL encodes a BigInteger.
     */
    private static String base64UrlEncode(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }

    /**
     * Base64 URL decodes a string to bytes.
     */
    private static byte[] base64UrlDecode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new PolicyEvaluationException("Invalid Base64 URL encoding: " + exception.getMessage(), exception);
        }
    }
}
