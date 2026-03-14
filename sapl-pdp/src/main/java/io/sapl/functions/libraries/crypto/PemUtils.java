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
package io.sapl.functions.libraries.crypto;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ERROR_INVALID_BASE64;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.util.Base64;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utilities for handling PEM-encoded cryptographic data. Uses Bouncy Castle for
 * PEM parsing and encoding, eliminating manual header/footer handling.
 */
@UtilityClass
public class PemUtils {

    private static final String ERROR_NO_PEM_CONTENT      = "No PEM content found.";
    private static final String ERROR_PEM_DECODING_FAILED = "PEM decoding failed: %s.";
    private static final String ERROR_PEM_ENCODING_FAILED = "PEM encoding failed: %s.";

    /**
     * Decodes PEM-encoded content from a string to raw DER bytes.
     *
     * @param pemContent
     * the PEM-formatted content string
     *
     * @return the decoded DER bytes
     *
     * @throws CryptoException
     * if no PEM content is found or decoding fails
     */
    public static byte[] decodePem(String pemContent) {
        try (val reader = new PemReader(new StringReader(pemContent))) {
            val pemObject = reader.readPemObject();
            if (pemObject == null) {
                throw new CryptoException(ERROR_NO_PEM_CONTENT);
            }
            return pemObject.getContent();
        } catch (IOException e) {
            throw new CryptoException(ERROR_NO_PEM_CONTENT, e);
        } catch (DecoderException e) {
            throw new CryptoException(ERROR_PEM_DECODING_FAILED.formatted(e.getMessage()), e);
        }
    }

    /**
     * Decodes PEM-encoded content from a file to raw DER bytes.
     *
     * @param file
     * the path to the PEM file
     *
     * @return the decoded DER bytes
     *
     * @throws IOException
     * if the file cannot be read
     * @throws CryptoException
     * if no PEM content is found
     */
    public static byte[] decodePemFromFile(Path file) throws IOException {
        try (val reader = new PemReader(Files.newBufferedReader(file))) {
            val pemObject = reader.readPemObject();
            if (pemObject == null) {
                throw new CryptoException(ERROR_NO_PEM_CONTENT);
            }
            return pemObject.getContent();
        } catch (DecoderException e) {
            throw new CryptoException(ERROR_PEM_DECODING_FAILED.formatted(e.getMessage()), e);
        }
    }

    /**
     * Decodes PEM-encoded public key content to raw DER bytes.
     *
     * @param pemKey
     * the PEM-encoded public key
     *
     * @return the decoded key bytes
     *
     * @throws CryptoException
     * if decoding fails
     */
    public static byte[] decodePublicKeyPem(String pemKey) {
        return decodePem(pemKey);
    }

    /**
     * Decodes certificate content to raw DER bytes. Handles both PEM format
     * (with headers) and raw Base64 DER format.
     *
     * @param certificateString
     * the certificate string in PEM or Base64 DER format
     *
     * @return the decoded certificate bytes
     *
     * @throws CryptoException
     * if decoding fails
     */
    public static byte[] decodeCertificatePem(String certificateString) {
        if (certificateString.contains("BEGIN CERTIFICATE")) {
            return decodePem(certificateString);
        }
        return decodeBase64(certificateString, "certificate");
    }

    /**
     * Encodes raw DER bytes to a PEM-formatted string.
     *
     * @param type
     * the PEM type label (e.g., "PUBLIC KEY", "PRIVATE KEY")
     * @param derBytes
     * the DER-encoded bytes
     *
     * @return the PEM-formatted string
     *
     * @throws CryptoException
     * if encoding fails
     */
    public static String encodePem(String type, byte[] derBytes) {
        try (val stringWriter = new StringWriter(); val pemWriter = new PemWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject(type, derBytes));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new CryptoException(ERROR_PEM_ENCODING_FAILED.formatted(e.getMessage()), e);
        }
    }

    /**
     * Encodes a public key in PEM format.
     *
     * @param keyBytes
     * the public key DER bytes
     *
     * @return the PEM-formatted public key string
     */
    public static String encodePublicKeyPem(byte[] keyBytes) {
        return encodePem("PUBLIC KEY", keyBytes);
    }

    /**
     * Writes a JCA key object to a file in PEM format.
     *
     * @param file
     * the output file path
     * @param key
     * the key to write
     *
     * @throws IOException
     * if the file cannot be written
     */
    public static void writeKeyToFile(Path file, Key key) throws IOException {
        try (val writer = new JcaPEMWriter(Files.newBufferedWriter(file))) {
            writer.writeObject(key);
        }
    }

    private static byte[] decodeBase64(String content, String context) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new CryptoException(ERROR_INVALID_BASE64.formatted(context, exception.getMessage()), exception);
        }
    }

}
