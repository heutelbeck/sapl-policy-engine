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
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service for encoding and decoding playground state into shareable permalinks.
 * Handles compression, encoding, validation, and security checks.
 * <p/>
 * Current implementation uses conservative limits for cross-browser
 * compatibility:
 * - MAX_COMPRESSED_SIZE_BYTES = 100,000 (100KB compressed)
 * - After base64 encoding: approximately 133KB
 * - Well within all modern browser limits
 * - Provides substantial capacity for complex playground states
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermalinkService {

    /*
     * Maximum uncompressed size in bytes before compression.
     * Prevents excessive memory usage and DoS attacks.
     */
    private static final int MAX_UNCOMPRESSED_SIZE_BYTES = 500_000;

    /*
     * Maximum compressed size in bytes before base64 encoding.
     * After base64 encoding, results in approximately 133KB.
     * Conservative limit ensuring compatibility with all modern browsers.
     * Modern Edge/Chrome can handle 2MB URLs, but 100KB provides
     * excellent cross-browser compatibility while allowing substantial
     * playground state sharing.
     */
    private static final int MAX_COMPRESSED_SIZE_BYTES = 100_000;

    /*
     * Maximum number of policy documents allowed in a permalink.
     * Prevents abuse and ensures reasonable state size.
     */
    private static final int MAX_POLICIES_COUNT = 20;

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
            val jsonString      = mapper.writeValueAsString(state);
            val jsonBytes       = jsonString.getBytes(StandardCharsets.UTF_8);
            val compressedBytes = compress(jsonBytes);

            if (compressedBytes.length > MAX_COMPRESSED_SIZE_BYTES) {
                throw new PermalinkException("Compressed state exceeds maximum size of " + MAX_COMPRESSED_SIZE_BYTES
                        + " bytes. Current size: " + compressedBytes.length);
            }

            return Base64.getUrlEncoder().withoutPadding().encodeToString(compressedBytes);

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
        validateEncodedString(encoded);

        try {
            val compressedBytes = Base64.getUrlDecoder().decode(encoded);
            validateCompressedSize(compressedBytes);

            val decompressedBytes = decompress(compressedBytes);
            validateDecompressedSize(decompressedBytes);

            val jsonString = new String(decompressedBytes, StandardCharsets.UTF_8);
            val state      = mapper.readValue(jsonString, PlaygroundState.class);

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

    /*
     * Validates encoded string before decoding.
     */
    private void validateEncodedString(String encoded) throws PermalinkException {
        if (encoded == null || encoded.isEmpty()) {
            throw new PermalinkException("Encoded permalink is empty");
        }

        if (encoded.length() > MAX_COMPRESSED_SIZE_BYTES * 2) {
            throw new PermalinkException("Encoded permalink exceeds maximum length");
        }
    }

    /*
     * Validates compressed data size.
     */
    private void validateCompressedSize(byte[] compressedBytes) throws PermalinkException {
        if (compressedBytes.length > MAX_COMPRESSED_SIZE_BYTES) {
            throw new PermalinkException("Compressed data exceeds maximum size");
        }
    }

    /*
     * Validates decompressed data size.
     */
    private void validateDecompressedSize(byte[] decompressedBytes) throws PermalinkException {
        if (decompressedBytes.length > MAX_UNCOMPRESSED_SIZE_BYTES) {
            throw new PermalinkException("Decompressed data exceeds maximum size");
        }
    }

    /*
     * Validates playground state before encoding.
     */
    private void validateStateForEncoding(PlaygroundState state) throws PermalinkException {
        validateStateNotNull(state);
        validatePolicies(state.policies(), true);
        validateSubscription(state.subscription());
        validateVariables(state.variables());
        validateCombiningAlgorithm(state.combiningAlgorithm());
        validateSelectedPolicyIndex(state.selectedPolicyIndex(), state.policies().size());
        validateContentFormat(state.subscription(), state.variables());
    }

    /*
     * Validates decoded playground state.
     */
    private void validateDecodedState(PlaygroundState state) throws PermalinkException {
        validateStateNotNull(state);
        validatePolicies(state.policies(), false);
        validateEachPolicy(state.policies());
        validateSubscription(state.subscription());
        validateVariables(state.variables());
        validateCombiningAlgorithm(state.combiningAlgorithm());
        validateSelectedPolicyIndex(state.selectedPolicyIndex(), state.policies().size());
        validateContentFormat(state.subscription(), state.variables());
    }

    /*
     * Validates that state is not null.
     */
    private void validateStateNotNull(PlaygroundState state) throws PermalinkException {
        if (state == null) {
            throw new PermalinkException("Playground state is null");
        }
    }

    /*
     * Validates policies list and count.
     */
    private void validatePolicies(Collection<String> policies, boolean requireNonEmpty) throws PermalinkException {
        if (policies == null) {
            throw new PermalinkException("Policies list is null");
        }

        if (requireNonEmpty && policies.isEmpty()) {
            throw new PermalinkException("At least one policy is required");
        }

        if (policies.size() > MAX_POLICIES_COUNT) {
            throw new PermalinkException("Too many policies (max: " + MAX_POLICIES_COUNT + ")");
        }
    }

    /*
     * Validates each individual policy document.
     */
    private void validateEachPolicy(List<String> policies) throws PermalinkException {
        for (var policy : policies) {
            if (policy == null) {
                throw new PermalinkException("Policy document is null");
            }
            if (policy.length() > MAX_UNCOMPRESSED_SIZE_BYTES / MAX_POLICIES_COUNT) {
                throw new PermalinkException("Individual policy document too large");
            }
        }
    }

    /*
     * Validates subscription is not null or empty.
     */
    private void validateSubscription(String subscription) throws PermalinkException {
        if (subscription == null || subscription.isEmpty()) {
            throw new PermalinkException("Authorization subscription is required");
        }
    }

    /*
     * Validates variables is not null or empty.
     */
    private void validateVariables(String variables) throws PermalinkException {
        if (variables == null || variables.isEmpty()) {
            throw new PermalinkException("Variables document is required");
        }
    }

    /*
     * Validates combining algorithm is not null.
     */
    private void validateCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) throws PermalinkException {
        if (algorithm == null) {
            throw new PermalinkException("Combining algorithm is required");
        }
    }

    /*
     * Validates selected policy index is within bounds.
     */
    private void validateSelectedPolicyIndex(Integer index, int policiesCount) throws PermalinkException {
        if (index != null && (index < 0 || index >= policiesCount)) {
            throw new PermalinkException("Selected policy index out of bounds");
        }
    }

    /*
     * Validates subscription and variables content format.
     */
    private void validateContentFormat(String subscription, String variables) throws PermalinkException {
        val subscriptionResult = validator.validateAuthorizationSubscription(subscription);
        if (!subscriptionResult.isValid()) {
            throw new PermalinkException("Invalid authorization subscription: " + subscriptionResult.message());
        }

        val variablesResult = validator.validateVariablesJson(variables);
        if (!variablesResult.isValid()) {
            throw new PermalinkException("Invalid variables document: " + variablesResult.message());
        }
    }

    /*
     * Compresses byte array using GZIP.
     */
    private byte[] compress(byte[] data) throws IOException {
        val outputStream = new ByteArrayOutputStream();
        try (val gzipStream = new GZIPOutputStream(outputStream)) {
            gzipStream.write(data);
        }
        return outputStream.toByteArray();
    }

    /*
     * Decompresses GZIP-compressed byte array.
     */
    private byte[] decompress(byte[] compressedData) throws IOException {
        val inputStream  = new ByteArrayInputStream(compressedData);
        val outputStream = new ByteArrayOutputStream();

        try (val gzipStream = new GZIPInputStream(inputStream)) {
            val buffer         = new byte[1024];
            int bytesRead;
            int totalBytesRead = 0;

            while ((bytesRead = gzipStream.read(buffer)) > 0) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_UNCOMPRESSED_SIZE_BYTES) {
                    throw new IOException("Decompressed data exceeds maximum size");
                }
                outputStream.write(buffer, 0, bytesRead);
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
