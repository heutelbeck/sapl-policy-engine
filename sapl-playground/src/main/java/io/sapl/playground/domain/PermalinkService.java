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
package io.sapl.playground.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.CombiningAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service for encoding and decoding playground state into shareable permalinks.
 * Handles compression, encoding,
 * validation, and security checks.
 * <p/>
 * Current implementation uses conservative limits for cross-browser
 * compatibility: - MAX_COMPRESSED_SIZE_BYTES =
 * 100,000 (100KB compressed) - After base64 encoding: approximately 133KB -
 * Well within all modern browser limits -
 * Provides substantial capacity for complex playground states
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermalinkService {

    /*
     * Maximum uncompressed size in bytes before compression. Prevents excessive
     * memory usage and DoS attacks.
     */
    private static final int MAX_UNCOMPRESSED_SIZE_BYTES = 500_000;

    /*
     * Maximum compressed size in bytes before base64 encoding. After base64
     * encoding, results in approximately 133KB.
     * Conservative limit ensuring compatibility with all modern browsers. Modern
     * Edge/Chrome can handle 2MB URLs, but
     * 100KB provides excellent cross-browser compatibility while allowing
     * substantial playground state sharing.
     */
    private static final int MAX_COMPRESSED_SIZE_BYTES = 100_000;

    /*
     * Maximum number of policy documents allowed in a permalink. Prevents abuse and
     * ensures reasonable state size.
     */
    private static final int MAX_POLICIES_COUNT = 20;

    private static final String ERROR_AT_LEAST_ONE_POLICY_REQUIRED     = "At least one policy is required";
    private static final String ERROR_AUTHORIZATION_SUBSCRIPTION       = "Authorization subscription is required";
    private static final String ERROR_COMBINING_ALGORITHM_REQUIRED     = "Combining algorithm is required";
    private static final String ERROR_COMPRESSED_DATA_EXCEEDS_MAX_SIZE = "Compressed data exceeds maximum size";
    private static final String ERROR_COMPRESSED_STATE_EXCEEDS_MAX     = "Compressed state exceeds maximum size of %d bytes. Current size: %d";
    private static final String ERROR_CORRUPTED_PERMALINK_DATA         = "Corrupted permalink data";
    private static final String ERROR_DECOMPRESSED_DATA_EXCEEDS_MAX    = "Decompressed data exceeds maximum size";
    private static final String ERROR_ENCODED_PERMALINK_EMPTY          = "Encoded permalink is empty";
    private static final String ERROR_ENCODED_PERMALINK_EXCEEDS_LENGTH = "Encoded permalink exceeds maximum length";
    private static final String ERROR_FAILED_COMPRESSION               = "Failed to create permalink: compression error";
    private static final String ERROR_FAILED_SERIALIZATION             = "Failed to create permalink: serialization error";
    private static final String ERROR_INDIVIDUAL_POLICY_TOO_LARGE      = "Individual policy document too large";
    private static final String ERROR_INVALID_AUTHORIZATION_SUBSCRIPT  = "Invalid authorization subscription: %s";
    private static final String ERROR_INVALID_PERMALINK_DATA           = "Invalid permalink data";
    private static final String ERROR_INVALID_PERMALINK_FORMAT         = "Invalid permalink format";
    private static final String ERROR_INVALID_VARIABLES_DOCUMENT       = "Invalid variables document: %s";
    private static final String ERROR_PLAYGROUND_STATE_NULL            = "Playground state is null";
    private static final String ERROR_POLICIES_LIST_NULL               = "Policies list is null";
    private static final String ERROR_POLICY_DOCUMENT_NULL             = "Policy document is null";
    private static final String ERROR_SELECTED_POLICY_INDEX_OUT_BOUNDS = "Selected policy index out of bounds";
    private static final String ERROR_TOO_MANY_POLICIES                = "Too many policies (max: %d)";
    private static final String ERROR_VARIABLES_DOCUMENT_REQUIRED      = "Variables document is required";

    private final JsonMapper          mapper;
    private final PlaygroundValidator validator;

    /**
     * Encodes playground state into a compressed, base64-encoded permalink.
     *
     * @param state
     * the playground state to encode
     *
     * @return the encoded permalink string
     *
     * @throws PermalinkException
     * if encoding fails or state is invalid
     */
    public String encode(PlaygroundState state) throws PermalinkException {
        validateStateForEncoding(state);

        try {
            val jsonString      = mapper.writeValueAsString(state);
            val jsonBytes       = jsonString.getBytes(StandardCharsets.UTF_8);
            val compressedBytes = compress(jsonBytes);

            if (compressedBytes.length > MAX_COMPRESSED_SIZE_BYTES) {
                throw new PermalinkException(ERROR_COMPRESSED_STATE_EXCEEDS_MAX.formatted(MAX_COMPRESSED_SIZE_BYTES,
                        compressedBytes.length));
            }

            return Base64.getUrlEncoder().withoutPadding().encodeToString(compressedBytes);

        } catch (JacksonException exception) {
            log.error("Failed to serialize playground state", exception);
            throw new PermalinkException(ERROR_FAILED_SERIALIZATION, exception);
        } catch (IOException exception) {
            log.error("Failed to compress playground state", exception);
            throw new PermalinkException(ERROR_FAILED_COMPRESSION, exception);
        }
    }

    /**
     * Decodes a permalink string back into playground state.
     *
     * @param encoded
     * the encoded permalink string
     *
     * @return the decoded playground state
     *
     * @throws PermalinkException
     * if decoding fails or data is invalid
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
            throw new PermalinkException(ERROR_INVALID_PERMALINK_FORMAT, exception);
        } catch (JacksonException exception) {
            log.debug("Failed to deserialize playground state", exception);
            throw new PermalinkException(ERROR_INVALID_PERMALINK_DATA, exception);
        } catch (IOException exception) {
            log.debug("Failed to decompress permalink data", exception);
            throw new PermalinkException(ERROR_CORRUPTED_PERMALINK_DATA, exception);
        }
    }

    /*
     * Validates encoded string before decoding.
     */
    private void validateEncodedString(String encoded) throws PermalinkException {
        if (encoded == null || encoded.isEmpty()) {
            throw new PermalinkException(ERROR_ENCODED_PERMALINK_EMPTY);
        }

        if (encoded.length() > MAX_COMPRESSED_SIZE_BYTES * 2) {
            throw new PermalinkException(ERROR_ENCODED_PERMALINK_EXCEEDS_LENGTH);
        }
    }

    /*
     * Validates compressed data size.
     */
    private void validateCompressedSize(byte[] compressedBytes) throws PermalinkException {
        if (compressedBytes.length > MAX_COMPRESSED_SIZE_BYTES) {
            throw new PermalinkException(ERROR_COMPRESSED_DATA_EXCEEDS_MAX_SIZE);
        }
    }

    /*
     * Validates decompressed data size.
     */
    private void validateDecompressedSize(byte[] decompressedBytes) throws PermalinkException {
        if (decompressedBytes.length > MAX_UNCOMPRESSED_SIZE_BYTES) {
            throw new PermalinkException(ERROR_DECOMPRESSED_DATA_EXCEEDS_MAX);
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
            throw new PermalinkException(ERROR_PLAYGROUND_STATE_NULL);
        }
    }

    /*
     * Validates policies list and count.
     */
    private void validatePolicies(Collection<String> policies, boolean requireNonEmpty) throws PermalinkException {
        if (policies == null) {
            throw new PermalinkException(ERROR_POLICIES_LIST_NULL);
        }

        if (requireNonEmpty && policies.isEmpty()) {
            throw new PermalinkException(ERROR_AT_LEAST_ONE_POLICY_REQUIRED);
        }

        if (policies.size() > MAX_POLICIES_COUNT) {
            throw new PermalinkException(ERROR_TOO_MANY_POLICIES.formatted(MAX_POLICIES_COUNT));
        }
    }

    /*
     * Validates each individual policy document.
     */
    private void validateEachPolicy(List<String> policies) throws PermalinkException {
        for (var policy : policies) {
            if (policy == null) {
                throw new PermalinkException(ERROR_POLICY_DOCUMENT_NULL);
            }
            if (policy.length() > MAX_UNCOMPRESSED_SIZE_BYTES / MAX_POLICIES_COUNT) {
                throw new PermalinkException(ERROR_INDIVIDUAL_POLICY_TOO_LARGE);
            }
        }
    }

    /*
     * Validates subscription is not null or empty.
     */
    private void validateSubscription(String subscription) throws PermalinkException {
        if (subscription == null || subscription.isEmpty()) {
            throw new PermalinkException(ERROR_AUTHORIZATION_SUBSCRIPTION);
        }
    }

    /*
     * Validates variables is not null or empty.
     */
    private void validateVariables(String variables) throws PermalinkException {
        if (variables == null || variables.isEmpty()) {
            throw new PermalinkException(ERROR_VARIABLES_DOCUMENT_REQUIRED);
        }
    }

    /*
     * Validates combining algorithm is not null.
     */
    private void validateCombiningAlgorithm(CombiningAlgorithm algorithm) throws PermalinkException {
        if (algorithm == null) {
            throw new PermalinkException(ERROR_COMBINING_ALGORITHM_REQUIRED);
        }
    }

    /*
     * Validates selected policy index is within bounds.
     */
    private void validateSelectedPolicyIndex(Integer index, int policiesCount) throws PermalinkException {
        if (index != null && (index < 0 || index >= policiesCount)) {
            throw new PermalinkException(ERROR_SELECTED_POLICY_INDEX_OUT_BOUNDS);
        }
    }

    /*
     * Validates subscription and variables content format.
     */
    private void validateContentFormat(String subscription, String variables) throws PermalinkException {
        val subscriptionResult = validator.validateAuthorizationSubscription(subscription);
        if (!subscriptionResult.isValid()) {
            throw new PermalinkException(ERROR_INVALID_AUTHORIZATION_SUBSCRIPT.formatted(subscriptionResult.message()));
        }

        val variablesResult = validator.validateVariablesJson(variables);
        if (!variablesResult.isValid()) {
            throw new PermalinkException(ERROR_INVALID_VARIABLES_DOCUMENT.formatted(variablesResult.message()));
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
                    throw new IOException(ERROR_DECOMPRESSED_DATA_EXCEEDS_MAX);
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return outputStream.toByteArray();
    }

    /**
     * Represents the complete playground state for permalinks.
     *
     * @param policies
     * list of policy documents
     * @param subscription
     * authorization subscription JSON
     * @param variables
     * variables JSON
     * @param combiningAlgorithm
     * the combining algorithm to use
     * @param selectedPolicyIndex
     * the index of the selected policy tab (null if variables tab selected)
     */
    public record PlaygroundState(
            List<String> policies,
            String subscription,
            String variables,
            CombiningAlgorithm combiningAlgorithm,
            Integer selectedPolicyIndex) {}

    /**
     * Exception thrown when permalink encoding/decoding fails.
     */
    @StandardException
    public static class PermalinkException extends Exception {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;
    }
}
