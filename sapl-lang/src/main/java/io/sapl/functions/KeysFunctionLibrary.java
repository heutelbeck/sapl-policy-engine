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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
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
@RequiredArgsConstructor
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

    private final ObjectMapper mapper;

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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
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
        } catch (Exception exception) {
            return Val.error("Failed to convert JWK to public key: " + exception.getMessage());
        }
    }

    /**
     * Decodes a PEM-encoded public key.
     *
     * @param pemKey the PEM-encoded key
     * @return the PublicKey
     * @throws Exception if decoding fails
     */
    private PublicKey decodePublicKey(String pemKey) throws Exception {
        val cleanedPem = pemKey.replaceAll("-----BEGIN PUBLIC KEY-----", "").replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        val keyBytes = Base64.getDecoder().decode(cleanedPem);
        val keySpec  = new X509EncodedKeySpec(keyBytes);

        try {
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception rsaException) {
            try {
                return KeyFactory.getInstance("EC").generatePublic(keySpec);
            } catch (Exception ecException) {
                try {
                    return KeyFactory.getInstance("EdDSA").generatePublic(keySpec);
                } catch (Exception edException) {
                    throw new Exception("Unsupported key type or invalid key format");
                }
            }
        }
    }

    /**
     * Decodes a certificate from PEM or DER format.
     *
     * @param certString the certificate string
     * @return the X509Certificate
     * @throws Exception if decoding fails
     */
    private X509Certificate decodeCertificate(String certString) throws Exception {
        val    certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] certBytes;

        if (certString.contains("BEGIN CERTIFICATE")) {
            val pemContent = certString.replaceAll("-----BEGIN CERTIFICATE-----", "")
                    .replaceAll("-----END CERTIFICATE-----", "").replaceAll("\\s+", "");
            certBytes = Base64.getDecoder().decode(pemContent);
        } else {
            certBytes = Base64.getDecoder().decode(certString);
        }

        val inputStream = new ByteArrayInputStream(certBytes);
        return (X509Certificate) certificateFactory.generateCertificate(inputStream);
    }

    /**
     * Converts a PublicKey to PEM format.
     *
     * @param publicKey the public key
     * @return the PEM string
     */
    private String publicKeyToPem(PublicKey publicKey) {
        val encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Builds a JSON object representation of a public key.
     *
     * @param publicKey the public key
     * @return the JSON object
     */
    private JsonNode buildKeyObject(PublicKey publicKey) {
        val keyObject = JSON.objectNode();
        keyObject.put("algorithm", publicKey.getAlgorithm());
        keyObject.put("format", publicKey.getFormat());
        keyObject.put("size", getKeySize(publicKey));

        if (publicKey instanceof ECPublicKey ecKey) {
            val curveName = ecKey.getParams().toString();
            keyObject.put("curve", extractCurveNameFromParams(curveName));
        }

        return keyObject;
    }

    /**
     * Gets the key size in bits.
     *
     * @param publicKey the public key
     * @return the key size
     */
    private int getKeySize(PublicKey publicKey) {
        if (publicKey instanceof RSAPublicKey rsaKey) {
            return rsaKey.getModulus().bitLength();
        } else if (publicKey instanceof ECPublicKey ecKey) {
            return ecKey.getParams().getOrder().bitLength();
        } else if (publicKey instanceof EdECPublicKey) {
            return 256;
        }
        return 0;
    }

    /**
     * Extracts the curve name from EC parameters string.
     *
     * @param paramsString the parameters string
     * @return the curve name
     */
    private String extractCurveNameFromParams(String paramsString) {
        if (paramsString.contains("secp256r1") || paramsString.contains("prime256v1")) {
            return "secp256r1";
        } else if (paramsString.contains("secp384r1")) {
            return "secp384r1";
        } else if (paramsString.contains("secp521r1")) {
            return "secp521r1";
        }
        return "unknown";
    }

    /**
     * Converts a PublicKey to JWK format.
     *
     * @param publicKey the public key
     * @return the JWK JSON object
     * @throws Exception if conversion fails
     */
    private JsonNode convertPublicKeyToJwk(PublicKey publicKey) throws Exception {
        val jwk = JSON.objectNode();

        if (publicKey instanceof RSAPublicKey rsaKey) {
            jwk.put("kty", "RSA");
            jwk.put("n", base64UrlEncode(rsaKey.getModulus()));
            jwk.put("e", base64UrlEncode(rsaKey.getPublicExponent()));
        } else if (publicKey instanceof ECPublicKey ecKey) {
            jwk.put("kty", "EC");
            jwk.put("crv", getCurveNameForJwk(ecKey));
            jwk.put("x", base64UrlEncode(ecKey.getW().getAffineX()));
            jwk.put("y", base64UrlEncode(ecKey.getW().getAffineY()));
        } else if (publicKey instanceof EdECPublicKey edKey) {
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(edKey.getEncoded()));
        } else {
            throw new Exception("Unsupported key type for JWK conversion");
        }

        return jwk;
    }

    /**
     * Converts a JWK to PublicKey.
     *
     * @param jwkNode the JWK JSON object
     * @return the PublicKey
     * @throws Exception if conversion fails
     */
    private PublicKey convertJwkToPublicKey(JsonNode jwkNode) throws Exception {
        val kty = jwkNode.get("kty").asText();

        if ("RSA".equals(kty)) {
            val n        = base64UrlDecode(jwkNode.get("n").asText());
            val e        = base64UrlDecode(jwkNode.get("e").asText());
            val modulus  = new BigInteger(1, n);
            val exponent = new BigInteger(1, e);

            val keySpec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } else if ("EC".equals(kty)) {
            throw new Exception("EC JWK to PublicKey conversion not yet implemented");
        } else if ("OKP".equals(kty)) {
            throw new Exception("OKP JWK to PublicKey conversion not yet implemented");
        }

        throw new Exception("Unsupported JWK key type: " + kty);
    }

    /**
     * Gets the JWK curve name from an EC key.
     *
     * @param ecKey the EC public key
     * @return the curve name
     */
    private String getCurveNameForJwk(ECPublicKey ecKey) {
        val bitLength = ecKey.getParams().getOrder().bitLength();
        return switch (bitLength) {
        case 256 -> "P-256";
        case 384 -> "P-384";
        case 521 -> "P-521";
        default  -> "unknown";
        };
    }

    /**
     * Base64 URL encodes a BigInteger.
     *
     * @param value the big integer
     * @return the encoded string
     */
    private String base64UrlEncode(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
    }

    /**
     * Base64 URL decodes a string to bytes.
     *
     * @param encoded the encoded string
     * @return the decoded bytes
     */
    private byte[] base64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
