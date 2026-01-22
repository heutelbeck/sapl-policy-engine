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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Provides Message Authentication Code functions for verifying message
 * integrity and authenticity using secret keys.
 * <p>
 * HMACs combine cryptographic hash functions with a secret key to produce a
 * message authentication code. They are used
 * to verify both the integrity and authenticity of messages, commonly in
 * webhook signatures, API authentication, and
 * secure communication protocols.
 * <p>
 * The library includes timing-safe comparison functions to prevent timing
 * attacks when verifying MACs.
 */
@UtilityClass
@FunctionLibrary(name = MacFunctionLibrary.NAME, description = MacFunctionLibrary.DESCRIPTION, libraryDocumentation = MacFunctionLibrary.DOCUMENTATION)
public class MacFunctionLibrary {

    public static final String NAME          = "mac";
    public static final String DESCRIPTION   = "Message Authentication Code functions for verifying message integrity and authenticity using HMAC algorithms.";
    public static final String DOCUMENTATION = """
            # Message Authentication Code Library

            Verify message integrity and authenticity using HMAC algorithms with secret keys.
            Use MACs when authorization decisions depend on cryptographic verification of
            external data like webhook signatures, API tokens, or signed messages.

            ## When to Use

            Verify webhook signatures from external services:
            ```sapl
            policy "verify-github-webhook"
            permit action == "process_webhook";
              var payload = resource.body;
              var receivedSignature = resource.headers["X-Hub-Signature-256"];
              var secret = environment.webhookSecret;
              mac.isValidHmac(payload, receivedSignature, secret, "HmacSHA256");
            ```

            Generate and verify signatures for API authentication:
            ```sapl
            policy "verify-api-request"
            permit action == "api_call";
              var requestData = resource.method + resource.path + resource.body;
              var expectedMac = mac.hmacSha256(requestData, subject.apiSecret);
              var receivedMac = resource.headers["X-Signature"];
              mac.timingSafeEquals(receivedMac, expectedMac);
            ```

            Validate signed resource identifiers to ensure integrity:
            ```sapl
            policy "verify-signed-resource-id"
            permit action == "access_resource";
              var resourceData = resource.id + "|" + resource.permissions;
              var expectedMac = mac.hmacSha256(resourceData, environment.signingKey);
              mac.timingSafeEquals(resource.signature, expectedMac);
            ```

            ## Security Considerations

            Always use timing-safe comparison when verifying MACs. The timingSafeEquals
            and isValidHmac functions use constant-time comparison to prevent timing attacks
            where an attacker could determine the correct MAC by measuring comparison time.

            Never use string equality or standard comparison operators to verify MACs in
            authorization decisions.
            """;

    private static final String RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String ERROR_FAILED_TO_COMPARE_MACS = "Failed to compare MACs: ";
    private static final String ERROR_FAILED_TO_COMPUTE_HMAC = "Failed to compute HMAC: ";
    private static final String ERROR_HMAC_ALGORITHM_UNAVAIL = "HMAC algorithm not available: ";
    private static final String ERROR_INVALID_HEX_MAC_FORMAT = "Invalid hexadecimal MAC format: ";
    private static final String ERROR_INVALID_HMAC_KEY       = "Invalid key for HMAC: ";

    /* HMAC Functions */

    @Function(docs = """
            ```hmacSha256(TEXT message, TEXT key)```: Computes HMAC-SHA256 authentication code.

            Generates a keyed-hash message authentication code using SHA-256. The key should
            be provided as a hexadecimal or Base64 string. Returns the MAC as a lowercase
            hexadecimal string.

            Commonly used for webhook signatures (GitHub, Stripe) and API authentication.

            **Examples:**
            ```sapl
            policy "example"
            permit
              var message = "hello world";
              var key = "secret";
              var mac = mac.hmacSha256(message, key);
              mac == "734cc62f32841568f45715aeb9f4d7891324e6d948e4c6c60c0621cdac48623a";
            ```
            """, schema = RETURNS_TEXT)
    public static Value hmacSha256(TextValue message, TextValue key) {
        return computeHmac(message.value(), key.value(), "HmacSHA256");
    }

    @Function(docs = """
            ```hmacSha384(TEXT message, TEXT key)```: Computes HMAC-SHA384 authentication code.

            Generates a keyed-hash message authentication code using SHA-384. Provides
            stronger security than HMAC-SHA256. Returns the MAC as a lowercase hexadecimal
            string.

            **Examples:**
            ```sapl
            policy "example"
            permit
              var message = "hello world";
              var key = "secret";
              var mac = mac.hmacSha384(message, key);
              mac == "0a0521b49a65e43c6991c456e5b37bcf44a3225a5e85e16b5e5a18821b41447de49d44ddcb38b3206c9c6952d5aab074";
            ```
            """, schema = RETURNS_TEXT)
    public static Value hmacSha384(TextValue message, TextValue key) {
        return computeHmac(message.value(), key.value(), "HmacSHA384");
    }

    @Function(docs = """
            ```hmacSha512(TEXT message, TEXT key)```: Computes HMAC-SHA512 authentication code.

            Generates a keyed-hash message authentication code using SHA-512. Provides
            the strongest security in the HMAC-SHA2 family. Returns the MAC as a lowercase
            hexadecimal string.

            **Examples:**
            ```sapl
            policy "example"
            permit
              var message = "hello world";
              var key = "secret";
              var mac = mac.hmacSha512(message, key);
              mac == "fef74d78b1e0d9180258835c7e855f0c9aa53d07d2a84088d62cef0218df0a3de20e69936a13b9ba0d36fb208aef0c6df6e00bf3a28f936f48faad8e6e8e2e39";
            ```
            """, schema = RETURNS_TEXT)
    public static Value hmacSha512(TextValue message, TextValue key) {
        return computeHmac(message.value(), key.value(), "HmacSHA512");
    }

    /* Verification Functions */

    @Function(docs = """
            ```timingSafeEquals(TEXT mac1, TEXT mac2)```: Compares two MACs using constant-time comparison.

            Performs a timing-safe comparison of two hexadecimal MAC strings. This prevents
            timing attacks where an attacker could determine the correct MAC by measuring
            comparison time. Always use this function when verifying MACs.

            The comparison is case-insensitive for hexadecimal strings.

            **Examples:**
            ```sapl
            policy "verify webhook"
            permit
              var receivedMac = "abc123";
              var computedMac = "abc123";
              mac.timingSafeEquals(receivedMac, computedMac);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value timingSafeEquals(TextValue mac1, TextValue mac2) {
        try {
            val bytes1 = hexToBytes(mac1.value());
            val bytes2 = hexToBytes(mac2.value());

            // MessageDigest.isEqual handles different lengths in constant time
            val areEqual = MessageDigest.isEqual(bytes1, bytes2);
            return Value.of(areEqual);
        } catch (IllegalArgumentException exception) {
            return new ErrorValue(ERROR_INVALID_HEX_MAC_FORMAT + exception.getMessage());
        } catch (Exception exception) {
            return new ErrorValue(ERROR_FAILED_TO_COMPARE_MACS + exception.getMessage());
        }
    }

    @Function(docs = """
            ```isValidHmac(TEXT message, TEXT expectedMac, TEXT key, TEXT algorithm)```: Verifies an HMAC signature.

            Computes the HMAC of the message using the provided key and algorithm, then
            performs a timing-safe comparison with the expected MAC. Returns true if they match.

            Supported algorithms: "HmacSHA256", "HmacSHA384", "HmacSHA512"

            **Examples:**
            ```sapl
            policy "verify webhook signature"
            permit
              var payload = "webhook payload";
              var signature = "expected_signature_from_header";
              var secret = "webhook_secret";
              mac.isValidHmac(payload, signature, secret, "HmacSHA256");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidHmac(TextValue message, TextValue expectedMac, TextValue key, TextValue algorithm) {
        val computedMac = computeHmac(message.value(), key.value(), algorithm.value());

        if (computedMac instanceof ErrorValue) {
            return computedMac;
        }

        return timingSafeEquals((TextValue) computedMac, expectedMac);
    }

    /**
     * Computes an HMAC using the specified algorithm.
     *
     * @param message
     * the message to authenticate
     * @param key
     * the secret key
     * @param algorithm
     * the HMAC algorithm name
     *
     * @return a Value containing the hexadecimal MAC or an error
     */
    private static Value computeHmac(String message, String key, String algorithm) {
        try {
            val secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
            val mac       = Mac.getInstance(algorithm);
            mac.init(secretKey);
            val hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            val hexHmac   = HexFormat.of().formatHex(hmacBytes);
            return Value.of(hexHmac);
        } catch (NoSuchAlgorithmException exception) {
            return new ErrorValue(ERROR_HMAC_ALGORITHM_UNAVAIL + algorithm);
        } catch (InvalidKeyException exception) {
            return new ErrorValue(ERROR_INVALID_HMAC_KEY + exception.getMessage());
        } catch (Exception exception) {
            return new ErrorValue(ERROR_FAILED_TO_COMPUTE_HMAC + exception.getMessage());
        }
    }

    /**
     * Converts a hexadecimal string to bytes.
     *
     * @param hex
     * the hexadecimal string
     *
     * @return the byte array
     *
     * @throws IllegalArgumentException
     * if the hex string is invalid
     */
    private static byte[] hexToBytes(String hex) {
        val cleanedHex = hex.strip().replace("_", "").toLowerCase();
        return HexFormat.of().parseHex(cleanedHex);
    }
}
