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
package io.sapl.playground.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service for encoding and decoding playground state into shareable permalinks.
 * Handles compression, encoding, validation, and security checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermalinkService {

    private static final int MAX_UNCOMPRESSED_SIZE_BYTES = 500_000;
    private static final int MAX_COMPRESSED_SIZE_BYTES   = 100_000;
    private static final int MAX_POLICIES_COUNT          = 20;

    private final ObjectMapper        mapper;
    private final PlaygroundValidator validator;

    /**
     * Encodes playground state into a compressed, base64-encoded permalink.
     *
     * @param state the playground state to encode
     * @return the encoded permalink string
     * @throws PermalinkException if encoding fails or state is invalid
     */
    public String encode(PlaygroundState state) throws PermalinkException {
        validateStateForEncoding(state);

        try {
            val json       = mapper.writeValueAsString(state);
            val jsonBytes  = json.getBytes(StandardCharsets.UTF_8);
            val compressed = compress(jsonBytes);

            if (compressed.length > MAX_COMPRESSED_SIZE_BYTES) {
                throw new PermalinkException("Compressed state exceeds maximum size of " + MAX_COMPRESSED_SIZE_BYTES
                        + " bytes. Current size: " + compressed.length);
            }

            return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);

        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize playground state", exception);
            throw new PermalinkException("Failed to create permalink: serialization error", exception);
        } catch (IOException exception) {
            log.error("Failed to compress playground state", exception);
            throw new PermalinkException("Failed to create permalink: compression error", exception);
        }
    }

    /**
     * Decodes a permalink string back into playground state.
     *
     * @param encoded the encoded permalink string
     * @return the decoded playground state
     * @throws PermalinkException if decoding fails or data is invalid
     */
    public PlaygroundState decode(String encoded) throws PermalinkException {
        if (encoded == null || encoded.isEmpty()) {
            throw new PermalinkException("Encoded permalink is empty");
        }

        if (encoded.length() > MAX_COMPRESSED_SIZE_BYTES * 2) {
            throw new PermalinkException("Encoded permalink exceeds maximum length");
        }

        try {
            val compressed = Base64.getUrlDecoder().decode(encoded);

            if (compressed.length > MAX_COMPRESSED_SIZE_BYTES) {
                throw new PermalinkException("Compressed data exceeds maximum size");
            }

            val decompressed = decompress(compressed);

            if (decompressed.length > MAX_UNCOMPRESSED_SIZE_BYTES) {
                throw new PermalinkException("Decompressed data exceeds maximum size");
            }

            val json  = new String(decompressed, StandardCharsets.UTF_8);
            val state = mapper.readValue(json, PlaygroundState.class);

            validateDecodedState(state);

            return state;

        } catch (IllegalArgumentException exception) {
            log.debug("Invalid base64 encoding in permalink", exception);
            throw new PermalinkException("Invalid permalink format", exception);
        } catch (JsonProcessingException exception) {
            log.debug("Failed to deserialize playground state", exception);
            throw new PermalinkException("Invalid permalink data", exception);
        } catch (IOException exception) {
            log.debug("Failed to decompress permalink data", exception);
            throw new PermalinkException("Corrupted permalink data", exception);
        }
    }

    /**
     * Validates playground state before encoding.
     *
     * @param state the state to validate
     * @throws PermalinkException if validation fails
     */
    private void validateStateForEncoding(PlaygroundState state) throws PermalinkException {
        if (state == null) {
            throw new PermalinkException("Playground state is null");
        }

        if (state.policies() == null || state.policies().isEmpty()) {
            throw new PermalinkException("At least one policy is required");
        }

        if (state.policies().size() > MAX_POLICIES_COUNT) {
            throw new PermalinkException("Too many policies (max: " + MAX_POLICIES_COUNT + ")");
        }

        if (state.subscription() == null || state.subscription().isEmpty()) {
            throw new PermalinkException("Authorization subscription is required");
        }

        if (state.variables() == null || state.variables().isEmpty()) {
            throw new PermalinkException("Variables document is required");
        }

        if (state.combiningAlgorithm() == null) {
            throw new PermalinkException("Combining algorithm is required");
        }

        if (state.selectedPolicyIndex() != null) {
            int index = state.selectedPolicyIndex();
            if (index < 0 || index >= state.policies().size()) {
                throw new PermalinkException("Selected policy index out of bounds");
            }
        }

        val subscriptionValidation = validator.validateAuthorizationSubscription(state.subscription());
        if (!subscriptionValidation.isValid()) {
            throw new PermalinkException("Invalid authorization subscription: " + subscriptionValidation.message());
        }

        val variablesValidation = validator.validateVariablesJson(state.variables());
        if (!variablesValidation.isValid()) {
            throw new PermalinkException("Invalid variables document: " + variablesValidation.message());
        }
    }

    /**
     * Validates decoded playground state.
     *
     * @param state the state to validate
     * @throws PermalinkException if validation fails
     */
    private void validateDecodedState(PlaygroundState state) throws PermalinkException {
        if (state == null) {
            throw new PermalinkException("Decoded state is null");
        }

        if (state.policies() == null) {
            throw new PermalinkException("Policies list is null");
        }

        if (state.policies().size() > MAX_POLICIES_COUNT) {
            throw new PermalinkException("Too many policies in permalink");
        }

        if (state.subscription() == null) {
            throw new PermalinkException("Subscription is null");
        }

        if (state.variables() == null) {
            throw new PermalinkException("Variables is null");
        }

        if (state.combiningAlgorithm() == null) {
            throw new PermalinkException("Combining algorithm is null");
        }

        if (state.selectedPolicyIndex() != null) {
            int index = state.selectedPolicyIndex();
            if (index < 0 || index >= state.policies().size()) {
                throw new PermalinkException("Selected policy index out of bounds");
            }
        }

        for (var policy : state.policies()) {
            if (policy == null) {
                throw new PermalinkException("Policy document is null");
            }
            if (policy.length() > MAX_UNCOMPRESSED_SIZE_BYTES / MAX_POLICIES_COUNT) {
                throw new PermalinkException("Individual policy document too large");
            }
        }

        val subscriptionValidation = validator.validateAuthorizationSubscription(state.subscription());
        if (!subscriptionValidation.isValid()) {
            throw new PermalinkException("Invalid subscription in permalink");
        }

        val variablesValidation = validator.validateVariablesJson(state.variables());
        if (!variablesValidation.isValid()) {
            throw new PermalinkException("Invalid variables in permalink");
        }
    }

    /**
     * Compresses byte array using GZIP.
     *
     * @param data the data to compress
     * @return compressed data
     * @throws IOException if compression fails
     */
    private byte[] compress(byte[] data) throws IOException {
        val outputStream = new ByteArrayOutputStream();
        try (val gzipStream = new GZIPOutputStream(outputStream)) {
            gzipStream.write(data);
        }
        return outputStream.toByteArray();
    }

    /**
     * Decompresses GZIP-compressed byte array.
     *
     * @param compressed the compressed data
     * @return decompressed data
     * @throws IOException if decompression fails
     */
    private byte[] decompress(byte[] compressed) throws IOException {
        val inputStream  = new ByteArrayInputStream(compressed);
        val outputStream = new ByteArrayOutputStream();

        try (val gzipStream = new GZIPInputStream(inputStream)) {
            val buffer    = new byte[1024];
            int length;
            int totalRead = 0;

            while ((length = gzipStream.read(buffer)) > 0) {
                totalRead += length;
                if (totalRead > MAX_UNCOMPRESSED_SIZE_BYTES) {
                    throw new IOException("Decompressed data exceeds maximum size");
                }
                outputStream.write(buffer, 0, length);
            }
        }

        return outputStream.toByteArray();
    }

    /**
     * Represents the complete playground state for permalinks.
     *
     * @param policies list of policy documents
     * @param subscription authorization subscription JSON
     * @param variables variables JSON
     * @param combiningAlgorithm the combining algorithm to use
     * @param selectedPolicyIndex the index of the selected policy tab (null if
     * variables tab selected)
     */
    public record PlaygroundState(
            List<String> policies,
            String subscription,
            String variables,
            PolicyDocumentCombiningAlgorithm combiningAlgorithm,
            Integer selectedPolicyIndex) {}

    /**
     * Exception thrown when permalink encoding/decoding fails.
     */
    public static class PermalinkException extends Exception {
        public PermalinkException(String message) {
            super(message);
        }

        public PermalinkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
