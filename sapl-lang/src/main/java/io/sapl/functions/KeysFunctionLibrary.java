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
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static io.sapl.functions.util.crypto.CryptoConstants.*;

/**
 * Provides functions for parsing and converting cryptographic key material.
 * Supports parsing public keys from PEM format, extracting keys from
 * certificates, and converting between different key representations.
 *
 * <p>Supported key types:
 * <ul>
 *   <li>RSA - Full bidirectional JWK conversion support</li>
 *   <li>EC (Elliptic Curve) - Full bidirectional JWK conversion support (P-256, P-384, P-521)</li>
 *   <li>EdDSA (Ed25519) - Full bidirectional JWK conversion support</li>
 * </ul>
 *
 * <p>JWK conversion follows RFC 7517 (JSON Web Key) and RFC 7518 (JSON Web Algorithms).
 */
@UtilityClass
@FunctionLibrary(name = KeysFunctionLibrary.NAME, description = KeysFunctionLibrary.DESCRIPTION, libraryDocumentation = KeysFunctionLibrary.DOCUMENTATION)
public class KeysFunctionLibrary {

    public static final String NAME        = "keys";
    public static final String DESCRIPTION = "Functions for parsing and converting cryptographic key material including PEM parsing and JWK conversion.";

    public static final String DOCUMENTATION = """
            # Keys Function Library

            Parse and convert cryptographic keys between PEM and JWK formats. Handles RSA keys at 2048 bits and above,
            EC keys on P-256, P-384, and P-521 curves, and EdDSA keys using Ed25519. All key types support full
            bidirectional PEM and JWK conversion following RFC 7517 and RFC 7518.

            Parse PEM-encoded keys and extract their properties with publicKeyFromPem, algorithmFromKey, sizeFromKey,
            and curveFromKey. Extract public keys from X.509 certificates using publicKeyFromCertificate. Convert
            between formats with jwkFromPublicKey (PEM to JWK) and publicKeyFromJwk (JWK to PEM).

            ## OAuth/OIDC Token Validation

            Fetch and use public keys from OAuth providers:
            
            ```sapl
            policy "validate access token"
            permit action == "api.call"
            where
              // Assume JWKS already retrieved via HTTP PIP
              var signingKey = keys.publicKeyFromJwk(resource.jwks.keys[0]);
              jwt.verify(request.token, signingKey);
            ```
            
            ## Certificate-Based Access Control
            
            Extract and validate keys from client certificates:
            
            ```sapl
            policy "require strong client cert"
            permit action == "admin.access"
            where
              var publicKey = keys.publicKeyFromCertificate(request.clientCert);
              var algorithm = keys.algorithmFromKey(publicKey);
              var keySize = keys.sizeFromKey(publicKey);
            
              algorithm == "RSA";
              keySize >= 2048;
            ```
            
            ## Key Type Enforcement
            
            Require specific cryptographic algorithms:
            
            ```sapl
            policy "require modern crypto"
            permit
            where
              var algorithm = keys.algorithmFromKey(subject.publicKey);
              var keyInfo = keys.publicKeyFromPem(subject.publicKey);
            
              // Allow only EC P-256 or Ed25519
              (algorithm == "EC" && keyInfo.curve == "secp256r1") ||
              (algorithm == "EdDSA");
            ```
            
            ## Microservice Authentication
            
            Publish service public keys as JWKs:
            
            ```sapl
            policy "register service"
            permit action == "service.register"
            where
              var serviceKey = keys.publicKeyFromPem(subject.publicKey);
              var jwk = keys.jwkFromPublicKey(subject.publicKey);
            
              // Store JWK for other services to use
              jwk.kty == "RSA";
              serviceKey.size >= 2048;
            ```
            
            ## Dynamic Key Validation
            
            Validate keys meet security requirements:
            
            ```sapl
            policy "enforce key policy"
            permit
            where
              var key = keys.publicKeyFromPem(resource.encryptionKey);
              var size = keys.sizeFromKey(resource.encryptionKey);
            
              key.algorithm in ["RSA", "EC"];
              size >= 2048;
            ```
            
            ## Certificate Chain Validation
            
            Extract and verify keys from certificate chains:
            
            ```sapl
            policy "validate cert chain"
            permit
            where
              var leafKey = keys.publicKeyFromCertificate(request.cert);
              var caKey = keys.publicKeyFromCertificate(trust.caCert);
            
              keys.algorithmFromKey(leafKey) == keys.algorithmFromKey(caKey);
              keys.sizeFromKey(leafKey) >= keys.sizeFromKey(caKey);
            ```
            
            ## Error Handling
            
            All functions return error values for invalid input:
            
            ```sapl
            policy "safe key handling"
            permit
            where
              var keyResult = keys.publicKeyFromPem(untrustedInput);
              keyResult.isDefined();  // Check before using
              keyResult.algorithm == "RSA";
            ```
            
            ## RFC Compliance

            JWK conversions follow RFC 7517 (JSON Web Key format), RFC 7518 (JSON Web Algorithms for RSA and EC),
            and RFC 8037 (CFRG Elliptic Curve for EdDSA/Ed25519). This ensures interoperability with OAuth providers,
            JWT libraries, and OIDC systems.
            
            ## Integration
            
            Works seamlessly with other SAPL libraries:
            
            ```sapl
            policy "complete auth flow"
            permit
            where
              // Assume JWKS already retrieved via HTTP PIP
              var key = keys.publicKeyFromJwk(resource.jwks.keys[0]);
            
              // Verify JWT
              jwt.verify(request.token, key);
            
              // Additional validation
              keys.sizeFromKey(key) >= 2048;
            ```
            
            ## Notes
            
            - PEM format must include `-----BEGIN PUBLIC KEY-----` headers
            - JWK fields use base64url encoding (no padding)
            - All conversions preserve key functionality (verified via signature operations)
            - Certificate extraction supports RSA and EC certificates
            """;

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

    private static final int ED25519_RAW_KEY_SIZE     = 32;
    private static final int ED25519_X509_HEADER_SIZE = 12;
    private static final int ED25519_X509_TOTAL_SIZE  = 44;

    /* Key Parsing */

    @Function(docs = """
            ```publicKeyFromPem(TEXT keyPem)```: Parses a PEM-encoded public key.

            Accepts RSA, EC (Elliptic Curve), and EdDSA public keys in PEM format.
            Returns a structured object with key type, algorithm, and format information.

            **Examples:**
            ```sapl
            policy "parse key"
            permit
            where
              var key = keys.publicKeyFromPem(publicKeyPem);
              key.algorithm == "RSA";
              key.format == "X.509";
            ```
            """, schema = RETURNS_OBJECT)
    public Val publicKeyFromPem(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            val keyObject = buildKeyObject(publicKey);
            return Val.of(keyObject);
        } catch (PolicyEvaluationException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to parse public key: " + errorMessage);
        }
    }

    @Function(docs = """
            ```publicKeyFromCertificate(TEXT certPem)```: Extracts the public key from an X.509 certificate.

            Parses the certificate and returns the embedded public key in PEM format.
            The returned key can be used with signature verification functions.

            **Examples:**
            ```sapl
            policy "extract key from cert"
            permit
            where
              var publicKey = keys.publicKeyFromCertificate(clientCert);
              signature.verifyRsaSha256(message, sig, publicKey);
            ```
            """, schema = RETURNS_TEXT)
    public Val publicKeyFromCertificate(@Text Val certPem) {
        try {
            val certificate = CertificateUtils.parseCertificate(certPem.getText());
            val publicKey   = certificate.getPublicKey();
            val keyPem      = PemUtils.encodePublicKeyPem(publicKey.getEncoded());
            return Val.of(keyPem);
        } catch (PolicyEvaluationException | CertificateException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to extract public key from certificate: " + errorMessage);
        }
    }

    /* Key Information Extraction */

    @Function(docs = """
            ```algorithmFromKey(TEXT keyPem)```: Extracts the algorithm name from a public key.

            Returns the key algorithm as a string: "RSA", "EC", or "EdDSA".

            **Examples:**
            ```sapl
            policy "check key type"
            permit
            where
              keys.algorithmFromKey(publicKey) == "RSA";
            ```
            """, schema = RETURNS_TEXT)
    public Val algorithmFromKey(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            return Val.of(publicKey.getAlgorithm());
        } catch (PolicyEvaluationException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to extract key algorithm: " + errorMessage);
        }
    }

    @Function(docs = """
            ```sizeFromKey(TEXT keyPem)```: Extracts the key size in bits.

            Returns the key size for RSA keys (e.g., 2048, 4096) or the curve size
            for EC keys (e.g., 256, 384). For EdDSA keys, returns the fixed key size.

            **Examples:**
            ```sapl
            policy "require strong keys"
            permit
            where
              keys.sizeFromKey(publicKey) >= 2048;
            ```
            """, schema = RETURNS_NUMBER)
    public Val sizeFromKey(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());
            val keySize   = KeyUtils.getKeySize(publicKey);
            return Val.of(keySize);
        } catch (PolicyEvaluationException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to extract key size: " + errorMessage);
        }
    }

    @Function(docs = """
            ```curveFromKey(TEXT keyPem)```: Extracts the elliptic curve name from an EC public key.

            Returns the curve name for elliptic curve keys (e.g., "secp256r1", "secp384r1", "secp521r1").
            Returns an error if the key is not an EC key.

            **Examples:**
            ```sapl
            policy "require p256 curve"
            permit
            where
              keys.curveFromKey(publicKey) == "secp256r1";
            ```
            """, schema = RETURNS_TEXT)
    public Val curveFromKey(@Text Val keyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(keyPem.getText());

            if (!(publicKey instanceof ECPublicKey ecKey)) {
                return Val.error("Key is not an EC key.");
            }

            val curveName = KeyUtils.extractEcCurveName(ecKey);
            return Val.of(curveName);
        } catch (PolicyEvaluationException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to extract EC curve: " + errorMessage);
        }
    }

    /* JWK Conversion */

    @Function(docs = """
            ```jwkFromPublicKey(TEXT publicKeyPem)```: Converts a PEM public key to JWK format.

            Converts RSA, EC, or EdDSA public keys to JSON Web Key (JWK) format as defined
            in RFC 7517. The JWK includes the key type (kty), algorithm parameters, and
            key material.

            Supported key types: RSA (all sizes), EC (P-256, P-384, P-521), EdDSA (Ed25519).

            **Examples:**
            ```sapl
            policy "convert to jwk"
            permit
            where
              var jwk = keys.jwkFromPublicKey(publicKeyPem);
              jwk.kty == "RSA";
              jwk.n != null;
            ```
            """, schema = RETURNS_OBJECT)
    public Val jwkFromPublicKey(@Text Val publicKeyPem) {
        try {
            val publicKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(publicKeyPem.getText());
            val jwk       = convertPublicKeyToJwk(publicKey);
            return Val.of(jwk);
        } catch (PolicyEvaluationException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to convert key to JWK: " + errorMessage);
        }
    }

    @Function(docs = """
            ```publicKeyFromJwk(OBJECT jwk)```: Converts a JWK to PEM public key format.

            Converts a JSON Web Key (JWK) object back to PEM format. Supports RSA, EC,
            and EdDSA keys. Returns the public key in standard PEM encoding.

            Supported JWK types: RSA (kty: "RSA"), EC (kty: "EC"), OKP (kty: "OKP" with Ed25519).

            **Examples:**
            ```sapl
            policy "convert from jwk"
            permit
            where
              var publicKeyPem = keys.publicKeyFromJwk(jwkObject);
              signature.verifyRsaSha256(message, sig, publicKeyPem);
            ```

            ```sapl
            policy "validate oauth token"
            permit action == "api.access"
            where
              // Assume JWKS already retrieved via HTTP PIP
              var publicKey = keys.publicKeyFromJwk(resource.jwks.keys[0]);
              var publicKey = keys.publicKeyFromJwk(jwk);
              jwt.verify(request.token, publicKey);
            ```
            """, schema = RETURNS_TEXT)
    public Val publicKeyFromJwk(@JsonObject Val jwk) {
        try {
            val publicKey = convertJwkToPublicKey(jwk.get());
            val keyPem    = PemUtils.encodePublicKeyPem(publicKey.getEncoded());
            return Val.of(keyPem);
        } catch (PolicyEvaluationException | NoSuchAlgorithmException | InvalidKeySpecException
                 | java.security.InvalidAlgorithmParameterException exception) {
            val message = exception.getMessage();
            val errorMessage = message.endsWith(".") ? message : message + ".";
            return Val.error("Failed to convert JWK to public key: " + errorMessage);
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
     * Converts a PublicKey to JWK format following RFC 7517 and RFC 7518.
     */
    private static JsonNode convertPublicKeyToJwk(PublicKey publicKey) {
        return switch (publicKey) {
            case RSAPublicKey rsaKey   -> convertRsaPublicKeyToJwk(rsaKey);
            case ECPublicKey ecKey     -> convertEcPublicKeyToJwk(ecKey);
            case EdECPublicKey edEcKey -> convertEdEcPublicKeyToJwk(edEcKey);
            default                    -> throw new PolicyEvaluationException(
                    "Unsupported key type for JWK conversion: " + publicKey.getClass().getName());
        };
    }

    /**
     * Converts an RSA public key to JWK format.
     */
    private static JsonNode convertRsaPublicKeyToJwk(RSAPublicKey rsaKey) {
        val jwk = JSON.objectNode();
        jwk.put("kty", JWK_KEY_TYPE_RSA);
        jwk.put("n", base64UrlEncode(rsaKey.getModulus()));
        jwk.put("e", base64UrlEncode(rsaKey.getPublicExponent()));
        return jwk;
    }

    /**
     * Converts an EC public key to JWK format.
     */
    private static JsonNode convertEcPublicKeyToJwk(ECPublicKey ecKey) {
        val jwk = JSON.objectNode();
        jwk.put("kty", JWK_KEY_TYPE_EC);
        jwk.put("crv", KeyUtils.getJwkCurveName(ecKey));
        jwk.put("x", base64UrlEncode(ecKey.getW().getAffineX()));
        jwk.put("y", base64UrlEncode(ecKey.getW().getAffineY()));
        return jwk;
    }

    /**
     * Converts an EdEC (Ed25519) public key to JWK format.
     * Extracts the raw 32-byte public key from the X.509 encoded structure.
     */
    private static JsonNode convertEdEcPublicKeyToJwk(EdECPublicKey edEcKey) {
        val jwk = JSON.objectNode();
        jwk.put("kty", JWK_KEY_TYPE_OKP);
        jwk.put("crv", CURVE_ED25519);

        // Extract raw Ed25519 key (32 bytes) from X.509 structure
        // X.509 format: 30 2a 30 05 06 03 2b 65 70 03 21 00 [32 bytes]
        val encoded = edEcKey.getEncoded();
        val rawKey  = extractRawEdEcPublicKey(encoded);
        jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey));

        return jwk;
    }

    /**
     * Extracts the raw Ed25519 public key bytes from X.509 encoded format.
     * The X.509 structure for Ed25519 ends with the 32-byte raw public key.
     */
    private static byte[] extractRawEdEcPublicKey(byte[] x509Encoded) {
        // Ed25519 X.509 format: fixed header followed by 32-byte key
        // Total length is 44 bytes, raw key is the last 32 bytes
        if (x509Encoded.length != ED25519_X509_TOTAL_SIZE) {
            throw new PolicyEvaluationException(
                    "Invalid Ed25519 X.509 encoding: expected " + ED25519_X509_TOTAL_SIZE + " bytes, got " + x509Encoded.length + ".");
        }
        return Arrays.copyOfRange(x509Encoded, ED25519_X509_HEADER_SIZE, ED25519_X509_TOTAL_SIZE);
    }

    /**
     * Converts a JWK to PublicKey following RFC 7517 and RFC 7518.
     */
    private static PublicKey convertJwkToPublicKey(JsonNode jwkNode)
            throws NoSuchAlgorithmException, InvalidKeySpecException, java.security.InvalidAlgorithmParameterException {
        val keyTypeNode = jwkNode.get("kty");
        if (keyTypeNode == null || !keyTypeNode.isTextual()) {
            throw new PolicyEvaluationException("JWK missing required 'kty' field.");
        }

        val keyType = keyTypeNode.asText();

        return switch (keyType) {
            case JWK_KEY_TYPE_RSA -> convertRsaJwkToPublicKey(jwkNode);
            case JWK_KEY_TYPE_EC  -> convertEcJwkToPublicKey(jwkNode);
            case JWK_KEY_TYPE_OKP -> convertOkpJwkToPublicKey(jwkNode);
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
            throw new PolicyEvaluationException("RSA JWK missing required 'n' (modulus) field.");
        }
        if (exponentNode == null || !exponentNode.isTextual()) {
            throw new PolicyEvaluationException("RSA JWK missing required 'e' (exponent) field.");
        }

        val modulusBytes  = base64UrlDecode(modulusNode.asText());
        val exponentBytes = base64UrlDecode(exponentNode.asText());
        val modulus       = new BigInteger(1, modulusBytes);
        val exponent      = new BigInteger(1, exponentBytes);

        val keySpec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance(ALGORITHM_RSA).generatePublic(keySpec);
    }

    /**
     * Converts an EC JWK to PublicKey.
     */
    private static PublicKey convertEcJwkToPublicKey(JsonNode jwkNode)
            throws NoSuchAlgorithmException, InvalidKeySpecException, java.security.InvalidAlgorithmParameterException {
        val curveNode = jwkNode.get("crv");
        val xNode     = jwkNode.get("x");
        val yNode     = jwkNode.get("y");

        if (curveNode == null || !curveNode.isTextual()) {
            throw new PolicyEvaluationException("EC JWK missing required 'crv' (curve) field.");
        }
        if (xNode == null || !xNode.isTextual()) {
            throw new PolicyEvaluationException("EC JWK missing required 'x' field.");
        }
        if (yNode == null || !yNode.isTextual()) {
            throw new PolicyEvaluationException("EC JWK missing required 'y' field.");
        }

        val curveName = curveNode.asText();
        val xBytes    = base64UrlDecode(xNode.asText());
        val yBytes    = base64UrlDecode(yNode.asText());

        // Convert JWK curve name to Java curve name
        val javaCurveName = switch (curveName) {
            case "P-256" -> "secp256r1";
            case "P-384" -> "secp384r1";
            case "P-521" -> "secp521r1";
            default      -> throw new PolicyEvaluationException("Unsupported EC curve: " + curveName);
        };

        // Build EC point and key spec
        val x     = new BigInteger(1, xBytes);
        val y     = new BigInteger(1, yBytes);
        val point = new java.security.spec.ECPoint(x, y);

        // Get the curve parameters by generating a temporary key
        val parameterSpec = new java.security.spec.ECGenParameterSpec(javaCurveName);
        val keyPairGen    = java.security.KeyPairGenerator.getInstance(ALGORITHM_EC);
        keyPairGen.initialize(parameterSpec);
        val tempKeyPair = keyPairGen.generateKeyPair();
        val tempEcKey   = (ECPublicKey) tempKeyPair.getPublic();
        val ecParams    = tempEcKey.getParams();

        val keySpec    = new java.security.spec.ECPublicKeySpec(point, ecParams);
        val keyFactory = KeyFactory.getInstance(ALGORITHM_EC);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Converts an OKP (EdDSA) JWK to PublicKey.
     */
    private static PublicKey convertOkpJwkToPublicKey(JsonNode jwkNode)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        val curveNode = jwkNode.get("crv");
        val xNode     = jwkNode.get("x");

        if (curveNode == null || !curveNode.isTextual()) {
            throw new PolicyEvaluationException("OKP JWK missing required 'crv' (curve) field.");
        }
        if (xNode == null || !xNode.isTextual()) {
            throw new PolicyEvaluationException("OKP JWK missing required 'x' field.");
        }

        val curveName = curveNode.asText();
        if (!CURVE_ED25519.equals(curveName)) {
            throw new PolicyEvaluationException("Unsupported OKP curve: " + curveName + " (only Ed25519 supported)");
        }

        val rawKeyBytes = base64UrlDecode(xNode.asText());

        // Reconstruct X.509 structure for Ed25519: 30 2a 30 05 06 03 2b 65 70 03 21 00 [32 bytes]
        val x509Encoded = reconstructEdEcX509Encoding(rawKeyBytes);

        val keySpec    = new X509EncodedKeySpec(x509Encoded);
        val keyFactory = KeyFactory.getInstance(ALGORITHM_ED25519);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Reconstructs X.509 encoding for Ed25519 public key from raw bytes.
     */
    private static byte[] reconstructEdEcX509Encoding(byte[] rawKey) {
        if (rawKey.length != ED25519_RAW_KEY_SIZE) {
            throw new PolicyEvaluationException(
                    "Invalid Ed25519 key length: expected " + ED25519_RAW_KEY_SIZE + " bytes, got " + rawKey.length + ".");
        }

        // X.509 header for Ed25519: 30 2a 30 05 06 03 2b 65 70 03 21 00
        val header = new byte[] { 0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00 };
        val result = new byte[ED25519_X509_TOTAL_SIZE];
        System.arraycopy(header, 0, result, 0, ED25519_X509_HEADER_SIZE);
        System.arraycopy(rawKey, 0, result, ED25519_X509_HEADER_SIZE, ED25519_RAW_KEY_SIZE);
        return result;
    }

    /**
     * Base64 URL encodes a BigInteger, stripping the sign byte if present.
     * Follows RFC 7518 unsigned big-endian integer encoding.
     */
    private static String base64UrlEncode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // Strip leading zero byte (sign byte for positive numbers)
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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