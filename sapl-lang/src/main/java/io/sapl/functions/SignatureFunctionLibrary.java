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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import io.sapl.functions.util.crypto.KeyUtils;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.HexFormat;

import static io.sapl.functions.util.crypto.CryptoConstants.*;

/**
 * Provides digital signature verification functions for verifying message
 * authenticity and integrity using public key cryptography.
 * <p>
 * Digital signatures use asymmetric cryptography where a private key signs
 * data and the corresponding public key verifies the signature. This library
 * supports RSA, ECDSA, and EdDSA signature verification for use in policy
 * evaluation.
 * <p>
 * Common use cases include API request signature verification, document
 * signing, and general authentication where the signer uses a private key
 * and the verifier has access to the public key.
 */
@UtilityClass
@FunctionLibrary(name = SignatureFunctionLibrary.NAME, description = SignatureFunctionLibrary.DESCRIPTION)
public class SignatureFunctionLibrary {

    public static final String NAME        = "signature";
    public static final String DESCRIPTION = "Digital signature verification functions for RSA, ECDSA, and EdDSA signatures using public key cryptography.";

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    /* RSA Signature Verification */

    @Function(docs = """
            ```verifyRsaSha256(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an RSA signature using SHA-256.

            Verifies that the signature was created by signing the message with the private key
            corresponding to the provided public key. The signature should be provided as a
            hexadecimal or Base64 string.

            Commonly used for API authentication and document signing.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "verify api signature"
            permit
            where
              var message = "request payload";
              var signature = "signature_from_header";
              var publicKey = "-----BEGIN PUBLIC KEY-----\\n...\\n-----END PUBLIC KEY-----";
              signature.verifyRsaSha256(message, signature, publicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyRsaSha256(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_RSA_SHA256,
                ALGORITHM_RSA);
    }

    @Function(docs = """
            ```verifyRsaSha384(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an RSA signature using SHA-384.

            Verifies RSA signatures using SHA-384 hash algorithm. Provides stronger security
            than SHA-256 for high-security applications.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "verify document signature"
            permit
            where
              signature.verifyRsaSha384(document, documentSignature, trustedPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyRsaSha384(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_RSA_SHA384,
                ALGORITHM_RSA);
    }

    @Function(docs = """
            ```verifyRsaSha512(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an RSA signature using SHA-512.

            Verifies RSA signatures using SHA-512 hash algorithm. Provides the strongest
            security in the RSA-SHA2 family.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The RSA public key in PEM format

            **Examples:**
            ```sapl
            policy "verify high-security signature"
            permit
            where
              signature.verifyRsaSha512(criticalData, dataSignature, certifiedPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyRsaSha512(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_RSA_SHA512,
                ALGORITHM_RSA);
    }

    /* ECDSA Signature Verification */

    @Function(docs = """
            ```verifyEcdsaP256(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an ECDSA signature using P-256 curve.

            Verifies ECDSA (Elliptic Curve Digital Signature Algorithm) signatures using the
            P-256 (secp256r1) curve with SHA-256. ECDSA provides equivalent security to RSA
            with smaller key sizes.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "verify ecdsa signature"
            permit
            where
              signature.verifyEcdsaP256(transaction, transactionSig, userPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyEcdsaP256(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_ECDSA_SHA256,
                ALGORITHM_EC);
    }

    @Function(docs = """
            ```verifyEcdsaP384(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an ECDSA signature using P-384 curve.

            Verifies ECDSA signatures using the P-384 (secp384r1) curve with SHA-384.
            Provides stronger security than P-256.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "verify ecdsa p384"
            permit
            where
              signature.verifyEcdsaP384(sensitiveData, dataSig, trustedEcKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyEcdsaP384(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_ECDSA_SHA384,
                ALGORITHM_EC);
    }

    @Function(docs = """
            ```verifyEcdsaP521(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an ECDSA signature using P-521 curve.

            Verifies ECDSA signatures using the P-521 (secp521r1) curve with SHA-512.
            Provides the strongest security in the NIST EC curves.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The EC public key in PEM format

            **Examples:**
            ```sapl
            policy "verify ecdsa p521"
            permit
            where
              signature.verifyEcdsaP521(highSecurityData, dataSig, ecPublicKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyEcdsaP521(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_ECDSA_SHA512,
                ALGORITHM_EC);
    }

    /* EdDSA Signature Verification */

    @Function(docs = """
            ```verifyEd25519(TEXT message, TEXT signature, TEXT publicKeyPem)```: Verifies an Ed25519 signature.

            Verifies EdDSA (Edwards-curve Digital Signature Algorithm) signatures using the
            Ed25519 curve. Ed25519 is a modern signature scheme that is fast, secure, and
            has small keys and signatures.

            Commonly used in modern cryptographic protocols and blockchain applications.

            **Parameters:**
            - message: The original message that was signed
            - signature: The signature in hexadecimal or Base64 format
            - publicKeyPem: The Ed25519 public key in PEM format

            **Examples:**
            ```sapl
            policy "verify ed25519 signature"
            permit
            where
              signature.verifyEd25519(blockData, blockSignature, validatorKey);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val verifyEd25519(@Text Val message, @Text Val signature, @Text Val publicKeyPem) {
        return verifySignature(message.getText(), signature.getText(), publicKeyPem.getText(), ALGORITHM_ED25519,
                ALGORITHM_EDDSA);
    }

    /* Helper Methods */

    /**
     * Verifies a digital signature using the specified algorithm. Parses the public
     * key,
     * decodes the signature, and performs cryptographic verification.
     *
     * @param message the original message that was signed
     * @param signatureString the signature in hexadecimal or Base64 format
     * @param publicKeyPem the public key in PEM format
     * @param signatureAlgorithm the signature algorithm name
     * @param keyAlgorithm the key algorithm name
     * @return Val containing true if signature is valid, Val.FALSE if invalid, or
     * an error
     */
    private static Val verifySignature(String message, String signatureString, String publicKeyPem,
            String signatureAlgorithm, String keyAlgorithm) {
        try {
            val publicKey      = KeyUtils.parsePublicKey(publicKeyPem, keyAlgorithm);
            val signatureBytes = parseSignature(signatureString);
            val messageBytes   = message.getBytes(StandardCharsets.UTF_8);

            val signature = Signature.getInstance(signatureAlgorithm);
            signature.initVerify(publicKey);
            signature.update(messageBytes);

            val isValid = signature.verify(signatureBytes);
            return Val.of(isValid);
        } catch (PolicyEvaluationException exception) {
            return Val.error(exception.getMessage());
        } catch (NoSuchAlgorithmException exception) {
            return Val.error("Signature algorithm not supported: " + signatureAlgorithm);
        } catch (InvalidKeyException exception) {
            return Val.error("Invalid public key: " + exception.getMessage());
        } catch (SignatureException exception) {
            return Val.error("Signature verification failed: " + exception.getMessage());
        } catch (Exception exception) {
            return Val.error("Failed to verify signature: " + exception.getMessage());
        }
    }

    /**
     * Parses a signature from hexadecimal or Base64 string format. Attempts hex
     * parsing first, then falls back to Base64 if hex parsing fails.
     *
     * @param signatureString the signature string in hex or Base64 format
     * @return the decoded signature bytes
     * @throws PolicyEvaluationException if both hex and Base64 parsing fail
     */
    private static byte[] parseSignature(String signatureString) {
        val cleanedSignature = signatureString.strip();

        try {
            return HexFormat.of().parseHex(cleanedSignature);
        } catch (IllegalArgumentException hexParsingFailed) {
            try {
                return Base64.getDecoder().decode(cleanedSignature);
            } catch (IllegalArgumentException base64ParsingFailed) {
                throw new PolicyEvaluationException(
                        "Signature must be in hexadecimal or Base64 format. Hex parsing failed: "
                                + hexParsingFailed.getMessage() + ". Base64 parsing failed: "
                                + base64ParsingFailed.getMessage(),
                        base64ParsingFailed);
            }
        }
    }
}
