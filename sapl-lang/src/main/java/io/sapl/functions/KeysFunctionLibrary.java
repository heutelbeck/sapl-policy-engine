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
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Provides functions for parsing and converting cryptographic key material.
 * <p>
 * Supports parsing public keys from PEM format, extracting keys from
 * certificates, and converting between different key representations. This
 * library focuses on public key operations relevant to policy evaluation.
 * <p>
 * Private key operations are intentionally limited as policy engines should
 * verify signatures rather than create them.
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

    private static final String PEM_PUBLIC_KEY_BEGIN  = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_PUBLIC_KEY_END    = "-----END PUBLIC KEY-----";
    private static final String PEM_CERTIFICATE_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_CERTIFICATE_END   = "-----END CERTIFICATE-----";

    private static final String ALGORITHM_RSA   = "RSA";
    private static final String ALGORITHM_EC    = "EC";
    private static final String ALGORITHM_EDDSA = "EdDSA";

    private static final String JWK_KEY_TYPE_RSA = "RSA";
    private static final String JWK_KEY_TYPE_EC  = "EC";
    private static final String JWK_KEY_TYPE_OKP = "OKP";

    private static final String CURVE_SECP256R1   = "secp256r1";
    private static final String CURVE_PRIME256V1  = "prime256v1";
    private static final String CURVE_SECP384R1   = "secp384r1";
    private static final String CURVE_SECP521R1   = "secp521r1";
    private static final String CURVE_ED25519     = "Ed25519";
    private static final String CURVE_P256_JWK    = "P-256";
    private static final String CURVE_P384_JWK    = "P-384";
    private static final String CURVE_P521_JWK    = "P-521";
    private static final String CURVE_UNKNOWN     = "unknown";

    private static final int EDDSA_KEY_SIZE_BITS = 256;
    private static final int EC_P256_BITS        = 256;
    private static final int EC_P384_BITS        = 384;
    private static final int EC_P521_BITS        = 521;

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
            val publicKey = decodePublicKey(keyPem.getText());
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
            val certificate = decodeCertificate(certPem.getText());
            val publicKey   = certificate.getPublicKey();
            val keyPem      = publicKeyToPem(publicKey);
            return Val.of(keyPem);
        } catch (PolicyEvaluationException exception) {
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
            val publicKey = decodePublicKey(keyPem.getText());
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
            val publicKey = decodePublicKey(keyPem.getText());
            val keySize   = getKeySize(publicKey);
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
            val publicKey = decodePublicKey(keyPem.getText());

            if (!(publicKey instanceof ECPublicKey ecKey)) {
                return Val.error("Key is not an EC key");
            }

            val curveName = ecKey.getParams().toString();
            return Val.of(extractCurveNameFromParams(curveName));
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
            val publicKey = decodePublicKey(publicKeyPem.getText());
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
            val keyPem    = publicKeyToPem(publicKey);
            return Val.of(keyPem);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to convert JWK to public key: " + exception.getMessage());
        }
    }

    /**
     * Removes PEM headers, footers, and whitespace from encoded content.
     *
     * @param pemContent the PEM-formatted content
     * @param beginMarker the begin marker to remove
     * @param endMarker the end marker to remove
     * @return the cleaned Base64 content
     */
    private static String cleanPemContent(String pemContent, String beginMarker, String endMarker) {
        return pemContent.replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s+", "");
    }

    /**
     * Decodes a PEM-encoded public key by attempting each supported algorithm.
     *
     * @param pemKey the PEM-encoded key
     * @return the PublicKey
     * @throws PolicyEvaluationException if decoding fails for all supported algorithms
     */
    private static PublicKey decodePublicKey(String pemKey) {
        val cleanedPem = cleanPemContent(pemKey, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);
        val keyBytes   = decodeBase64(cleanedPem, "Invalid Base64 encoding in public key");
        val keySpec    = new X509EncodedKeySpec(keyBytes);

        return tryDecodePublicKeyWithAlgorithms(keySpec);
    }

    /**
     * Attempts to decode a public key using all supported algorithms.
     *
     * @param keySpec the key specification
     * @return the decoded PublicKey
     * @throws PolicyEvaluationException if all algorithms fail
     */
    private static PublicKey tryDecodePublicKeyWithAlgorithms(X509EncodedKeySpec keySpec) {
        val algorithms = new String[] { ALGORITHM_RSA, ALGORITHM_EC, ALGORITHM_EDDSA };

        for (val algorithm : algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // Try next algorithm
            }
        }

        throw new PolicyEvaluationException("Unsupported key type or invalid key format. Tried: RSA, EC, EdDSA");
    }

    /**
     * Decodes a certificate from PEM or DER format.
     *
     * @param certificateString the certificate string
     * @return the X509Certificate
     * @throws PolicyEvaluationException if decoding fails
     */
    private static X509Certificate decodeCertificate(String certificateString) {
        val certificateFactory = getCertificateFactory();
        val certificateBytes   = extractCertificateBytes(certificateString);
        val inputStream        = new ByteArrayInputStream(certificateBytes);

        try {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        } catch (CertificateException exception) {
            throw new PolicyEvaluationException("Failed to parse X.509 certificate", exception);
        }
    }

    /**
     * Gets the X.509 certificate factory.
     *
     * @return the CertificateFactory
     * @throws PolicyEvaluationException if X.509 is not supported
     */
    private static CertificateFactory getCertificateFactory() {
        try {
            return CertificateFactory.getInstance("X.509");
        } catch (CertificateException exception) {
            throw new PolicyEvaluationException("X.509 certificate factory not available", exception);
        }
    }

    /**
     * Extracts certificate bytes from PEM or Base64 DER format.
     *
     * @param certificateString the certificate string
     * @return the decoded certificate bytes
     * @throws PolicyEvaluationException if Base64 decoding fails
     */
    private static byte[] extractCertificateBytes(String certificateString) {
        if (certificateString.contains("BEGIN CERTIFICATE")) {
            val pemContent = cleanPemContent(certificateString, PEM_CERTIFICATE_BEGIN, PEM_CERTIFICATE_END);
            return decodeBase64(pemContent, "Invalid Base64 encoding in certificate PEM");
        }
        return decodeBase64(certificateString, "Invalid Base64 encoding in certificate");
    }

    /**
     * Decodes Base64 content with error context.
     *
     * @param content the Base64-encoded content
     * @param errorContext the context description for error messages
     * @return the decoded bytes
     * @throws PolicyEvaluationException if decoding fails
     */
    private static byte[] decodeBase64(String content, String errorContext) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new PolicyEvaluationException(errorContext + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Converts a PublicKey to PEM format.
     *
     * @param publicKey the public key
     * @return the PEM string
     */
    private static String publicKeyToPem(PublicKey publicKey) {
        val encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return PEM_PUBLIC_KEY_BEGIN + '\n' + encoded + '\n' + PEM_PUBLIC_KEY_END;
    }

    /**
     * Builds a JSON object representation of a public key.
     *
     * @param publicKey the public key
     * @return the JSON object
     */
    private static JsonNode buildKeyObject(PublicKey publicKey) {
        val keyObject = JSON.objectNode();
        keyObject.put("algorithm", publicKey.getAlgorithm());
        keyObject.put("format", publicKey.getFormat());
        keyObject.put("size", getKeySize(publicKey));

        if (publicKey instanceof ECPublicKey ecKey) {
            val curveParametersString = ecKey.getParams().toString();
            keyObject.put("curve", extractCurveNameFromParams(curveParametersString));
        }

        return keyObject;
    }

    /**
     * Gets the key size in bits.
     *
     * @param publicKey the public key
     * @return the key size in bits
     */
    private static int getKeySize(PublicKey publicKey) {
        return switch (publicKey) {
            case RSAPublicKey rsaKey -> rsaKey.getModulus().bitLength();
            case ECPublicKey ecKey -> ecKey.getParams().getOrder().bitLength();
            case EdECPublicKey edEcKey -> EDDSA_KEY_SIZE_BITS;
            default -> 0;
        };
    }

    /**
     * Extracts the curve name from EC parameters string.
     *
     * @param parametersString the parameters string representation
     * @return the standardized curve name
     */
    private static String extractCurveNameFromParams(String parametersString) {
        if (parametersString.contains(CURVE_SECP256R1) || parametersString.contains(CURVE_PRIME256V1)) {
            return CURVE_SECP256R1;
        }
        if (parametersString.contains(CURVE_SECP384R1)) {
            return CURVE_SECP384R1;
        }
        if (parametersString.contains(CURVE_SECP521R1)) {
            return CURVE_SECP521R1;
        }
        return CURVE_UNKNOWN;
    }

    /**
     * Converts a PublicKey to JWK format.
     *
     * @param publicKey the public key
     * @return the JWK JSON object
     * @throws PolicyEvaluationException if conversion fails or key type is unsupported
     */
    private static JsonNode convertPublicKeyToJwk(PublicKey publicKey) {
        return switch (publicKey) {
            case RSAPublicKey rsaKey -> {
                val jwk = JSON.objectNode();
                jwk.put("kty", JWK_KEY_TYPE_RSA);
                jwk.put("n", base64UrlEncode(rsaKey.getModulus()));
                jwk.put("e", base64UrlEncode(rsaKey.getPublicExponent()));
                yield jwk;
            }
            case ECPublicKey ecKey -> {
                val jwk = JSON.objectNode();
                jwk.put("kty", JWK_KEY_TYPE_EC);
                jwk.put("crv", getCurveNameForJwk(ecKey));
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
            default -> throw new PolicyEvaluationException(
                    "Unsupported key type for JWK conversion: " + publicKey.getClass().getName());
        };
    }

    /**
     * Converts a JWK to PublicKey.
     *
     * @param jwkNode the JWK JSON object
     * @return the PublicKey
     * @throws PolicyEvaluationException if conversion fails or key type is unsupported
     */
    private static PublicKey convertJwkToPublicKey(JsonNode jwkNode) {
        val keyTypeNode = jwkNode.get("kty");
        if (keyTypeNode == null || !keyTypeNode.isTextual()) {
            throw new PolicyEvaluationException("JWK missing required 'kty' field");
        }

        val keyType = keyTypeNode.asText();

        return switch (keyType) {
            case JWK_KEY_TYPE_RSA -> convertRsaJwkToPublicKey(jwkNode);
            case JWK_KEY_TYPE_EC -> throw new PolicyEvaluationException(
                    "EC JWK to PublicKey conversion not yet implemented");
            case JWK_KEY_TYPE_OKP -> throw new PolicyEvaluationException(
                    "OKP JWK to PublicKey conversion not yet implemented");
            default -> throw new PolicyEvaluationException("Unsupported JWK key type: " + keyType);
        };
    }

    /**
     * Converts an RSA JWK to PublicKey.
     *
     * @param jwkNode the RSA JWK JSON object
     * @return the RSA PublicKey
     * @throws PolicyEvaluationException if conversion fails or required fields are missing
     */
    private static PublicKey convertRsaJwkToPublicKey(JsonNode jwkNode) {
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
        try {
            return KeyFactory.getInstance(ALGORITHM_RSA).generatePublic(keySpec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new PolicyEvaluationException("Failed to generate RSA public key from JWK", exception);
        }
    }

    /**
     * Gets the JWK curve name from an EC key.
     *
     * @param ecKey the EC public key
     * @return the JWK curve name
     */
    private static String getCurveNameForJwk(ECPublicKey ecKey) {
        val bitLength = ecKey.getParams().getOrder().bitLength();
        return switch (bitLength) {
            case EC_P256_BITS -> CURVE_P256_JWK;
            case EC_P384_BITS -> CURVE_P384_JWK;
            case EC_P521_BITS -> CURVE_P521_JWK;
            default -> CURVE_UNKNOWN;
        };
    }

    /**
     * Base64 URL encodes a BigInteger.
     *
     * @param value the big integer
     * @return the Base64 URL encoded string without padding
     */
    private static String base64UrlEncode(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }

    /**
     * Base64 URL decodes a string to bytes.
     *
     * @param encoded the Base64 URL encoded string
     * @return the decoded bytes
     * @throws PolicyEvaluationException if decoding fails
     */
    private static byte[] base64UrlDecode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new PolicyEvaluationException("Invalid Base64 URL encoding: " + exception.getMessage(), exception);
        }
    }
}