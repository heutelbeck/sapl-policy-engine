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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.libraries.crypto.CryptoException;
import io.sapl.functions.libraries.crypto.KeyUtils;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.HexFormat;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_EC;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ECDSA_SHA256;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ECDSA_SHA384;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ECDSA_SHA512;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ED25519;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_EDDSA;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_RSA;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_RSA_SHA256;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_RSA_SHA384;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_RSA_SHA512;

/**
 * Provides digital signature verification functions for verifying message
 * authenticity and integrity using public key
 * cryptography.
 * <p>
 * Digital signatures use asymmetric cryptography where a private key signs data
 * and the corresponding public key
 * verifies the signature. This library supports RSA, ECDSA, and EdDSA signature
 * verification for use in policy
 * evaluation.
 * <p>
 * Common use cases include API request signature verification, document
 * signing, and general authentication where the
 * signer uses a private key and the verifier has access to the public key.
 * <p>
 * Signature Format Requirements:
 * <ul>
 * <li>Hexadecimal: lowercase or uppercase, no delimiters</li>
 * <li>Base64: standard or URL-safe encoding accepted</li>
 * <li>Whitespace is automatically stripped</li>
 * </ul>
 * <p>
 * Thread Safety: All verification functions are thread-safe and can be called
 * concurrently. The underlying
 * {@code java.security.Signature} implementation provides constant-time
 * comparison for signature verification on most
 * JVM implementations, providing protection against timing attacks.
 */
@UtilityClass
@FunctionLibrary(name = SignatureFunctionLibrary.NAME, description = SignatureFunctionLibrary.DESCRIPTION, libraryDocumentation = SignatureFunctionLibrary.DOCUMENTATION)
public class SignatureFunctionLibrary {

    public static final String NAME          = "signature";
    public static final String DESCRIPTION   = "Digital signature verification functions for RSA, ECDSA, and EdDSA signatures using public key cryptography.";
    public static final String DOCUMENTATION = """
            # Digital Signature Verification

            Verify digital signatures using public key cryptography. All functions take
            a message, signature, and PEM-encoded public key, returning true if valid.

            ## Algorithm Selection

            | Algorithm        | Function            | Key Size | Use Case                |
            |------------------|---------------------|----------|-------------------------|
            | RSA + SHA-256    | `isValidRsaSha256`  | 2048+    | General purpose, legacy |
            | RSA + SHA-512    | `isValidRsaSha512`  | 2048+    | High security RSA       |
            | ECDSA P-256      | `isValidEcdsaP256`  | 256-bit  | Modern, compact         |
            | ECDSA P-384      | `isValidEcdsaP384`  | 384-bit  | Government compliance   |
            | Ed25519          | `isValidEd25519`    | 256-bit  | Fast, modern default    |

            ## API Request Authentication

            Verify signed API requests before processing:

            ```sapl
            policy "verify api signature"
            permit
                action == "api:call"
            where
                var payload = action.requestBody;
                var sig = action.headers.signature;
                var pubKey = subject.registeredPublicKey;
                signature.isValidEd25519(payload, sig, pubKey);
            ```

            Example: `action.requestBody = "{"amount":100}"`, `action.headers.signature`
            is the Base64-encoded Ed25519 signature. `subject.registeredPublicKey` is
            a PEM string like `-----BEGIN PUBLIC KEY-----\\nMCo...\\n-----END PUBLIC KEY-----`.
            Returns true if the signature was created with the matching private key.

            ## Document Integrity Check

            Ensure documents haven't been tampered with:

            ```sapl
            policy "verify document"
            permit
                action == "view"
            where
                signature.isValidRsaSha256(
                    resource.content,
                    resource.signature,
                    environment.trustedSignerKey
                );
            ```

            Example: `resource.content` is the document text, `resource.signature` is
            hex-encoded (e.g., "3045022100..."). Signature format is auto-detected
            (hex or Base64). Returns true if document is authentic.
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String ERROR_ALGORITHM_NOT_SUPPORTED  = "Signature algorithm not supported: ";
    private static final String ERROR_FAILED_TO_VERIFY         = "Failed to verify signature: ";
    private static final String ERROR_INVALID_PUBLIC_KEY       = "Invalid public key: ";
    private static final String ERROR_SIGNATURE_FORMAT_INVALID = "Signature must be in hexadecimal or Base64 format. Hex parsing failed: %s. Base64 parsing failed: %s";
    private static final String ERROR_VERIFICATION_FAILED      = "Signature verification failed: ";

    @Function(docs = """
            ```isValidRsaSha256(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an RSA signature using SHA-256.

            Checks whether the signature was created using the private key corresponding to the
            public key. Signature must be in hexadecimal or Base64 format.

            Use for API authentication, document signing, and general RSA signature validation
            where SHA-256 hash strength is sufficient.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "api signature check"
            permit
              var message = "request payload";
              var signature = "signature_from_header";
              var publicKey = "-----BEGIN PUBLIC KEY-----\\n...\\n-----END PUBLIC KEY-----";
              signature.isValidRsaSha256(message, signature, publicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidRsaSha256(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_RSA_SHA256,
                ALGORITHM_RSA);
    }

    @Function(docs = """
            ```isValidRsaSha384(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an RSA signature using SHA-384.

            Checks RSA signatures using SHA-384 hash algorithm. Use when security policy
            requires stronger hashing than SHA-256.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "document signature"
            permit
              signature.isValidRsaSha384(document, documentSignature, trustedPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidRsaSha384(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_RSA_SHA384,
                ALGORITHM_RSA);
    }

    @Function(docs = """
            ```isValidRsaSha512(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an RSA signature using SHA-512.

            Checks RSA signatures using SHA-512 hash algorithm. Strongest hash in the RSA-SHA2
            family, use for high-security requirements.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "secure signature check"
            permit
              signature.isValidRsaSha512(criticalData, dataSignature, certifiedPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidRsaSha512(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_RSA_SHA512,
                ALGORITHM_RSA);
    }

    @Function(docs = """
            ```isValidEcdsaP256(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an ECDSA signature using P-256 curve.

            Checks ECDSA (Elliptic Curve Digital Signature Algorithm) signatures using the
            P-256 (secp256r1) curve with SHA-256. ECDSA gives equivalent security to RSA
            with smaller keys.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "transaction signature"
            permit
              signature.isValidEcdsaP256(transaction, transactionSig, userPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidEcdsaP256(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_ECDSA_SHA256,
                ALGORITHM_EC);
    }

    @Function(docs = """
            ```isValidEcdsaP384(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an ECDSA signature using P-384 curve.

            Checks ECDSA signatures using the P-384 (secp384r1) curve with SHA-384.
            Use when security policy requires stronger curves than P-256.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "ecdsa p384 check"
            permit
              signature.isValidEcdsaP384(sensitiveData, dataSig, trustedEcKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidEcdsaP384(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_ECDSA_SHA384,
                ALGORITHM_EC);
    }

    @Function(docs = """
            ```isValidEcdsaP521(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an ECDSA signature using P-521 curve.

            Checks ECDSA signatures using the P-521 (secp521r1) curve with SHA-512.
            Strongest NIST elliptic curve, use for highest security requirements.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "ecdsa p521 check"
            permit
              signature.isValidEcdsaP521(highSecurityData, dataSig, ecPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidEcdsaP521(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_ECDSA_SHA512,
                ALGORITHM_EC);
    }

    @Function(docs = """
            ```isValidEd25519(TEXT message, TEXT signature, TEXT publicKeyPem)```: Validates an Ed25519 signature.

            Checks EdDSA (Edwards-curve Digital Signature Algorithm) signatures using the
            Ed25519 curve. Ed25519 is fast, secure, and has small keys and signatures.

            Standard in SSH keys, TLS 1.3, Signal Protocol, and many blockchain implementations.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The Ed25519 public key in PEM format

            **Examples:**
            ```sapl
            policy "ed25519 signature check"
            permit
              signature.isValidEd25519(blockData, blockSignature, validatorKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidEd25519(TextValue message, TextValue signature, TextValue publicKeyPem) {
        return verifySignature(message.value(), signature.value(), publicKeyPem.value(), ALGORITHM_ED25519,
                ALGORITHM_EDDSA);
    }

    /**
     * Verifies a digital signature using the specified algorithm. Parses the public
     * key, decodes the signature, and
     * performs cryptographic verification.
     *
     * @param message
     * the original message that was signed
     * @param signatureString
     * the signature in hexadecimal or Base64 format
     * @param publicKeyPem
     * the public key in PEM format
     * @param signatureAlgorithm
     * the signature algorithm name
     * @param keyAlgorithm
     * the key algorithm name
     *
     * @return Value containing true if signature is valid, FALSE if invalid, or an
     * error
     */
    private static Value verifySignature(String message, String signatureString, String publicKeyPem,
            String signatureAlgorithm, String keyAlgorithm) {
        try {
            val publicKey      = KeyUtils.parsePublicKey(publicKeyPem, keyAlgorithm);
            val signatureBytes = parseSignature(signatureString);
            val messageBytes   = message.getBytes(StandardCharsets.UTF_8);

            val signature = Signature.getInstance(signatureAlgorithm);
            signature.initVerify(publicKey);
            signature.update(messageBytes);

            val isValid = signature.verify(signatureBytes);
            return Value.of(isValid);
        } catch (CryptoException exception) {
            return Value.error(exception.getMessage());
        } catch (NoSuchAlgorithmException exception) {
            return Value.error(ERROR_ALGORITHM_NOT_SUPPORTED + signatureAlgorithm);
        } catch (InvalidKeyException exception) {
            return Value.error(ERROR_INVALID_PUBLIC_KEY + exception.getMessage());
        } catch (SignatureException exception) {
            return Value.error(ERROR_VERIFICATION_FAILED + exception.getMessage());
        } catch (Exception exception) {
            return Value.error(ERROR_FAILED_TO_VERIFY + exception.getMessage());
        }
    }

    /**
     * Parses a signature from hexadecimal or Base64 string format.
     * <p>
     * Attempts hexadecimal parsing first, then falls back to Base64 if hex parsing
     * fails. This two-step approach
     * ensures maximum compatibility with various signature encoding formats
     * commonly used in APIs and protocols.
     *
     * @param signatureString
     * the signature string in hex or Base64 format
     *
     * @return the decoded signature bytes
     *
     * @throws CryptoException
     * if both hex and Base64 parsing fail
     */
    private static byte[] parseSignature(String signatureString) {
        val cleanedSignature = signatureString.strip();

        try {
            return HexFormat.of().parseHex(cleanedSignature);
        } catch (IllegalArgumentException hexParsingFailed) {
            try {
                return Base64.getDecoder().decode(cleanedSignature);
            } catch (IllegalArgumentException base64ParsingFailed) {
                throw new CryptoException(ERROR_SIGNATURE_FORMAT_INVALID.formatted(hexParsingFailed.getMessage(),
                        base64ParsingFailed.getMessage()), base64ParsingFailed);
            }
        }
    }
}
