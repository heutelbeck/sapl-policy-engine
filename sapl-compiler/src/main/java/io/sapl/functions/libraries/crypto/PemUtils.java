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
package io.sapl.functions.libraries.crypto;

import io.sapl.compiler.PolicyEvaluationException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Base64;

import static io.sapl.functions.libraries.crypto.CryptoConstants.*;

/**
 * Utilities for handling PEM-encoded cryptographic data. Provides methods for
 * stripping PEM headers/footers and decoding Base64 content.
 */
@UtilityClass
public class PemUtils {

    /**
     * Strips PEM headers and footers from encoded content, leaving only the
     * Base64 data.
     *
     * @param pemContent the PEM-formatted content
     * @param beginMarker the begin marker to remove (e.g., "-----BEGIN PUBLIC
     * KEY-----")
     * @param endMarker the end marker to remove (e.g., "-----END PUBLIC KEY-----")
     * @return the cleaned Base64 content without headers, footers, or whitespace
     */
    public static String stripPemHeaders(String pemContent, String beginMarker, String endMarker) {
        return pemContent.replace(beginMarker, "").replace(endMarker, "").replaceAll("\\s+", "");
    }

    /**
     * Decodes PEM-encoded public key content by stripping headers and decoding
     * Base64.
     *
     * @param pemKey the PEM-encoded public key
     * @return the decoded key bytes
     * @throws PolicyEvaluationException if Base64 decoding fails
     */
    public static byte[] decodePublicKeyPem(String pemKey) {
        val cleanedPem = stripPemHeaders(pemKey, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);
        return decodeBase64(cleanedPem, "public key");
    }

    /**
     * Decodes PEM-encoded certificate content by stripping headers and decoding
     * Base64. Handles both PEM format (with headers) and raw Base64 DER format.
     *
     * @param certificateString the certificate string in PEM or Base64 DER format
     * @return the decoded certificate bytes
     * @throws PolicyEvaluationException if Base64 decoding fails
     */
    public static byte[] decodeCertificatePem(String certificateString) {
        if (certificateString.contains("BEGIN CERTIFICATE")) {
            val cleanedPem = stripPemHeaders(certificateString, PEM_CERTIFICATE_BEGIN, PEM_CERTIFICATE_END);
            return decodeBase64(cleanedPem, "certificate");
        }
        return decodeBase64(certificateString, "certificate");
    }

    /**
     * Encodes a public key in PEM format with proper headers and Base64 encoding.
     *
     * @param keyBytes the public key bytes to encode
     * @return the PEM-formatted public key string
     */
    public static String encodePublicKeyPem(byte[] keyBytes) {
        val encoded = Base64.getEncoder().encodeToString(keyBytes);
        return PEM_PUBLIC_KEY_BEGIN + '\n' + encoded + '\n' + PEM_PUBLIC_KEY_END;
    }

    /**
     * Decodes Base64 content with contextual error messages.
     *
     * @param content the Base64-encoded content
     * @param context the context description for error messages (e.g., "public
     * key", "certificate")
     * @return the decoded bytes
     * @throws PolicyEvaluationException if decoding fails
     */
    private static byte[] decodeBase64(String content, String context) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new PolicyEvaluationException(
                    ERROR_INVALID_BASE64 + " in " + context + ": " + exception.getMessage() + ".", exception);
        }
    }
}
