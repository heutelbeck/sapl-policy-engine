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
package io.sapl.pdp.configuration.bundle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manifest containing file hashes and cryptographic signature for a SAPL
 * bundle.
 * <p>
 * The manifest provides integrity verification for bundle contents by recording
 * SHA-256 hashes of all files and an
 * Ed25519 signature over the canonical JSON representation. This ensures that
 * any modification to bundle contents can
 * be detected during verification.
 * </p>
 * <h2>Manifest Structure</h2>
 *
 * <pre>{@code
 * {
 *   "version": "1.0",
 *   "hashAlgorithm": "SHA-256",
 *   "created": "2024-01-15T10:30:00Z",
 *   "expires": "2025-01-15T10:30:00Z",
 *   "files": {
 *     "access-control.sapl": "sha256:7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069",
 *     "pdp.json": "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
 *   },
 *   "signature": {
 *     "algorithm": "Ed25519",
 *     "keyId": "production-key-2024",
 *     "value": "base64-signature"
 *   }
 * }
 * }</pre>
 *
 * <h2>Signing Process</h2>
 * <ol>
 * <li>Compute SHA-256 hash for each file in the bundle</li>
 * <li>Build manifest JSON without signature field</li>
 * <li>Serialize to canonical JSON (sorted keys, no extra whitespace)</li>
 * <li>Sign the canonical bytes with Ed25519</li>
 * <li>Add signature to manifest</li>
 * </ol>
 * <h2>Verification Process</h2>
 * <ol>
 * <li>Extract manifest from bundle</li>
 * <li>Remove signature field and re-serialize to canonical JSON</li>
 * <li>Verify Ed25519 signature over canonical bytes</li>
 * <li>Verify each file's hash matches the recorded hash</li>
 * <li>Verify no extra files exist in bundle (except manifest itself)</li>
 * </ol>
 *
 * @see BundleBuilder
 * @see BundleParser
 */
@JsonPropertyOrder({ "version", "hashAlgorithm", "created", "expires", "files", "signature" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BundleManifest(
        @JsonProperty("version") String version,
        @JsonProperty("hashAlgorithm") String hashAlgorithm,
        @JsonProperty("created") Instant created,
        @JsonProperty("expires") Instant expires,
        @JsonProperty("files") Map<String, String> files,
        @JsonProperty("signature") Signature signature) {

    /** Current manifest format version. */
    public static final String MANIFEST_VERSION = "1.0";

    /** Hash algorithm used for file integrity. */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** Signature algorithm. */
    public static final String SIGNATURE_ALGORITHM = "Ed25519";

    /** Filename for the manifest within the bundle. */
    public static final String MANIFEST_FILENAME = ".sapl-manifest.json";

    private static final ObjectMapper CANONICAL_MAPPER = createCanonicalMapper();

    /**
     * Signature block containing the cryptographic signature and metadata.
     *
     * @param algorithm
     * the signature algorithm (always "Ed25519")
     * @param keyId
     * identifier for the signing key, useful for key rotation
     * @param value
     * Base64-encoded signature bytes
     */
    @JsonPropertyOrder({ "algorithm", "keyId", "value" })
    public record Signature(
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("keyId") String keyId,
            @JsonProperty("value") String value) {}

    /**
     * Creates a new manifest builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Computes the SHA-256 hash of the given content.
     *
     * @param content
     * the content to hash
     *
     * @return the hash in "sha256:base64" format
     */
    public static String computeHash(String content) {
        return computeHash(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the SHA-256 hash of the given bytes.
     *
     * @param bytes
     * the bytes to hash
     *
     * @return the hash in "sha256:base64" format
     */
    public static String computeHash(byte[] bytes) {
        try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM);
            val hash   = digest.digest(bytes);
            return "sha256:" + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " not available.", e);
        }
    }

    /**
     * Serializes this manifest to canonical JSON for signing or verification.
     * <p>
     * The canonical form has sorted keys and minimal whitespace to ensure
     * consistent byte representation across
     * serialization/deserialization cycles.
     * </p>
     *
     * @return canonical JSON bytes
     */
    public byte[] toCanonicalBytes() {
        return toCanonicalBytes(this);
    }

    /**
     * Serializes a manifest (or partial manifest) to canonical JSON.
     *
     * @param manifest
     * the manifest to serialize
     *
     * @return canonical JSON bytes
     */
    public static byte[] toCanonicalBytes(BundleManifest manifest) {
        try {
            return CANONICAL_MAPPER.writeValueAsBytes(manifest);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize manifest.", e);
        }
    }

    /**
     * Creates a copy of this manifest without the signature field. Used for
     * signature verification.
     *
     * @return manifest without signature
     */
    public BundleManifest withoutSignature() {
        return new BundleManifest(version, hashAlgorithm, created, expires, files, null);
    }

    /**
     * Serializes this manifest to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        try {
            return CANONICAL_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize manifest.", e);
        }
    }

    /**
     * Parses a manifest from JSON.
     *
     * @param json
     * the JSON string
     *
     * @return parsed manifest
     *
     * @throws BundleSignatureException
     * if parsing fails
     */
    public static BundleManifest fromJson(String json) {
        try {
            return CANONICAL_MAPPER.readValue(json, BundleManifest.class);
        } catch (JsonProcessingException e) {
            throw new BundleSignatureException("Failed to parse manifest: " + e.getMessage(), e);
        }
    }

    private static ObjectMapper createCanonicalMapper() {
        val mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Builder for creating manifest instances.
     */
    public static class Builder {

        private Instant             created = Instant.now();
        private Instant             expires;
        private Map<String, String> files   = new TreeMap<>();
        private Signature           signature;

        /**
         * Sets the creation timestamp.
         *
         * @param created
         * creation time
         *
         * @return this builder
         */
        public Builder created(Instant created) {
            this.created = created;
            return this;
        }

        /**
         * Sets the expiration timestamp.
         *
         * @param expires
         * expiration time, or null for no expiration
         *
         * @return this builder
         */
        public Builder expires(Instant expires) {
            this.expires = expires;
            return this;
        }

        /**
         * Adds a file hash to the manifest.
         *
         * @param filename
         * the filename
         * @param content
         * the file content
         *
         * @return this builder
         */
        public Builder addFile(String filename, String content) {
            files.put(filename, computeHash(content));
            return this;
        }

        /**
         * Adds a file hash to the manifest.
         *
         * @param filename
         * the filename
         * @param content
         * the file content as bytes
         *
         * @return this builder
         */
        public Builder addFile(String filename, byte[] content) {
            files.put(filename, computeHash(content));
            return this;
        }

        /**
         * Adds a pre-computed hash to the manifest.
         *
         * @param filename
         * the filename
         * @param hash
         * the hash in "sha256:base64" format
         *
         * @return this builder
         */
        public Builder addFileHash(String filename, String hash) {
            files.put(filename, hash);
            return this;
        }

        /**
         * Sets all file hashes at once.
         *
         * @param files
         * map of filename to hash
         *
         * @return this builder
         */
        public Builder files(Map<String, String> files) {
            this.files = new TreeMap<>(files);
            return this;
        }

        /**
         * Sets the signature.
         *
         * @param signature
         * the signature block
         *
         * @return this builder
         */
        public Builder signature(Signature signature) {
            this.signature = signature;
            return this;
        }

        /**
         * Sets the signature from components.
         *
         * @param keyId
         * the signing key identifier
         * @param signatureValue
         * Base64-encoded signature
         *
         * @return this builder
         */
        public Builder signature(String keyId, String signatureValue) {
            this.signature = new Signature(SIGNATURE_ALGORITHM, keyId, signatureValue);
            return this;
        }

        /**
         * Builds the manifest without a signature. Use this to create the unsigned
         * manifest for signing.
         *
         * @return unsigned manifest
         */
        public BundleManifest buildUnsigned() {
            return new BundleManifest(MANIFEST_VERSION, HASH_ALGORITHM, created, expires, new TreeMap<>(files), null);
        }

        /**
         * Builds the manifest with signature.
         *
         * @return signed manifest
         */
        public BundleManifest build() {
            return new BundleManifest(MANIFEST_VERSION, HASH_ALGORITHM, created, expires, new TreeMap<>(files),
                    signature);
        }
    }
}
