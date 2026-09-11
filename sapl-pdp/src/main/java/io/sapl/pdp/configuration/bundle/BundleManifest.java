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
import io.sapl.api.SaplVersion;
import io.sapl.pdp.configuration.ConfigurationIds;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manifest carrying the packaging metadata, file hashes, and cryptographic
 * signature for a SAPL bundle.
 * <p>
 * The manifest is the carrier of the bundle's {@code configurationId} (its
 * publication identity) and provides integrity verification for bundle contents
 * by recording SHA-256 hashes of all files and an Ed25519 signature over the
 * canonical JSON representation. This ensures that any modification to bundle
 * contents can be detected during verification.
 * </p>
 * <h2>Manifest Structure</h2>
 *
 * <pre>{@code
 * {
 *   "version": "4.2.0",
 *   "hashAlgorithm": "SHA-256",
 *   "created": "2024-01-15T10:30:00Z",
 *   "configurationId": "release-77",
 *   "attribution": "sapl-node/4.2.0",
 *   "audience": {
 *     "sealingRecipient": "recipient-key-2024"
 *   },
 *   "files": {
 *     "access-control.sapl": "sha256:f4OxZX/x/FO5LcGBSKHWXfwtSx+j1ncoSt3SABJtkGk=",
 *     "pdp.json": "sha256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
 *   },
 *   "signature": {
 *     "algorithm": "Ed25519",
 *     "keyId": "production-key-2024",
 *     "value": "base64-signature"
 *   }
 * }
 * }</pre>
 * <p>
 * The {@code version} field is recorded provenance: it is minted at build time
 * from the engine library version that wrote the manifest and is not validated
 * on load. The {@code attribution} field is an arbitrary JSON string or object,
 * never interpreted by the engine. The {@code audience.sealingRecipient} field
 * names the single recipient key id of the bundle's sealed content and is
 * required exactly when sealed content is present. Parsing is strict: unknown
 * top-level fields and missing required fields are rejected fail-closed.
 * </p>
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "version", "hashAlgorithm", "created", "configurationId", "attribution", "audience", "files",
        "signature" })
public record BundleManifest(
        @JsonProperty("version") String version,
        @JsonProperty("hashAlgorithm") String hashAlgorithm,
        @JsonProperty("created") Instant created,
        @JsonProperty("configurationId") String configurationId,
        @JsonProperty("attribution") JsonNode attribution,
        @JsonProperty("audience") Audience audience,
        @JsonProperty("files") Map<String, String> files,
        @JsonProperty("signature") Signature signature) {

    /** Hash algorithm used for file integrity. */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** Maximum serialized size of the attribution value in bytes. */
    public static final int MAX_ATTRIBUTION_BYTES = 16 * 1024;

    /** Signature algorithm. */
    public static final String SIGNATURE_ALGORITHM = "Ed25519";

    /** Filename for the manifest within the bundle. */
    public static final String MANIFEST_FILENAME = ".sapl-manifest.json";

    private static final String MIGRATION_GUIDANCE = "The configurationId moved to the bundle manifest; rebuild the bundle with SAPL 4.2.0 or later.";

    private static final String ERROR_ATTRIBUTION_MISSING = "Manifest is missing required field 'attribution'. "
            + MIGRATION_GUIDANCE;
    private static final String ERROR_ATTRIBUTION_NOT_STRING_OR_OBJECT = "Manifest field 'attribution' must be a JSON string or object.";
    private static final String ERROR_ATTRIBUTION_TOO_LARGE = "Manifest field 'attribution' exceeds the maximum serialized size of %d bytes.";
    private static final String ERROR_AUDIENCE_SEALING_RECIPIENT_BLANK = "Manifest field 'audience.sealingRecipient' must not be blank when the audience block is present.";
    private static final String ERROR_CONFIGURATION_ID_INVALID = "Manifest field 'configurationId' with value '%s' is invalid. "
            + ConfigurationIds.VALIDITY_RULE;
    private static final String ERROR_CONFIGURATION_ID_MISSING = "Manifest is missing required field 'configurationId'. "
            + MIGRATION_GUIDANCE;
    private static final String ERROR_CREATED_MISSING = "Manifest is missing required field 'created'. "
            + MIGRATION_GUIDANCE;
    private static final String ERROR_FAILED_TO_PARSE_MANIFEST = "Failed to parse manifest: %s.";
    private static final String ERROR_FAILED_TO_SERIALIZE_MANIFEST = "Failed to serialize manifest.";
    private static final String ERROR_FILES_MISSING = "Manifest is missing required field 'files'.";
    private static final String ERROR_UNSUPPORTED_MANIFEST_HASH = "Manifest field 'hashAlgorithm' must be '%s', got: '%s'.";

    private static final JsonMapper CANONICAL_MAPPER = createCanonicalMapper();

    /**
     * Audience block naming the single recipient of the bundle's sealed content.
     *
     * @param sealingRecipient
     * the key id of the X25519 recipient key the sealed content is sealed to
     */
    public record Audience(@JsonProperty("sealingRecipient") String sealingRecipient) {}

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
        } catch (JacksonException e) {
            throw new IllegalStateException(ERROR_FAILED_TO_SERIALIZE_MANIFEST, e);
        }
    }

    /**
     * Creates a copy of this manifest without the signature field. Used for
     * signature verification.
     *
     * @return manifest without signature
     */
    public BundleManifest withoutSignature() {
        return new BundleManifest(version, hashAlgorithm, created, configurationId, attribution, audience, files, null);
    }

    /**
     * Creates a copy of this manifest with the given signature.
     *
     * @param newSignature the signature block
     * @return manifest with the signature
     */
    BundleManifest withSignature(Signature newSignature) {
        return new BundleManifest(version, hashAlgorithm, created, configurationId, attribution, audience, files,
                newSignature);
    }

    /**
     * Serializes this manifest to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        try {
            return CANONICAL_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JacksonException e) {
            throw new IllegalStateException(ERROR_FAILED_TO_SERIALIZE_MANIFEST, e);
        }
    }

    /**
     * Parses a manifest from JSON with strict validation: unknown top-level fields
     * and missing required fields are rejected fail-closed.
     *
     * @param json
     * the JSON string
     *
     * @return parsed manifest
     *
     * @throws BundleSignatureException
     * if parsing or validation fails
     */
    public static BundleManifest fromJson(String json) {
        BundleManifest manifest;
        try {
            manifest = CANONICAL_MAPPER.readValue(json, BundleManifest.class);
        } catch (JacksonException e) {
            throw new BundleSignatureException(ERROR_FAILED_TO_PARSE_MANIFEST.formatted(e.getMessage()), e);
        }
        validate(manifest);
        return manifest;
    }

    static JsonNode attributionOfText(String attributionTag) {
        return JsonNodeFactory.instance.stringNode(attributionTag);
    }

    static JsonNode parseAttributionJson(String attributionJson) {
        return CANONICAL_MAPPER.readTree(attributionJson);
    }

    private static void validate(BundleManifest manifest) {
        if (manifest.created() == null) {
            throw new BundleSignatureException(ERROR_CREATED_MISSING);
        }
        if (manifest.configurationId() == null) {
            throw new BundleSignatureException(ERROR_CONFIGURATION_ID_MISSING);
        }
        if (!ConfigurationIds.isValid(manifest.configurationId())) {
            throw new BundleSignatureException(ERROR_CONFIGURATION_ID_INVALID.formatted(manifest.configurationId()));
        }
        validateAttribution(manifest.attribution());
        if (manifest.audience() != null && (manifest.audience().sealingRecipient() == null
                || manifest.audience().sealingRecipient().isBlank())) {
            throw new BundleSignatureException(ERROR_AUDIENCE_SEALING_RECIPIENT_BLANK);
        }
        if (!HASH_ALGORITHM.equals(manifest.hashAlgorithm())) {
            throw new BundleSignatureException(
                    ERROR_UNSUPPORTED_MANIFEST_HASH.formatted(HASH_ALGORITHM, manifest.hashAlgorithm()));
        }
        if (manifest.files() == null) {
            throw new BundleSignatureException(ERROR_FILES_MISSING);
        }
    }

    private static void validateAttribution(JsonNode attribution) {
        if (attribution == null || attribution.isNull()) {
            throw new BundleSignatureException(ERROR_ATTRIBUTION_MISSING);
        }
        if (!attribution.isString() && !attribution.isObject()) {
            throw new BundleSignatureException(ERROR_ATTRIBUTION_NOT_STRING_OR_OBJECT);
        }
        val serializedSize = CANONICAL_MAPPER.writeValueAsBytes(attribution).length;
        if (serializedSize > MAX_ATTRIBUTION_BYTES) {
            throw new BundleSignatureException(ERROR_ATTRIBUTION_TOO_LARGE.formatted(MAX_ATTRIBUTION_BYTES));
        }
    }

    private static JsonMapper createCanonicalMapper() {
        return JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).findAndAddModules().build();
    }

    /**
     * Builder for creating manifest instances. The manifest {@code version} is
     * minted at build time from the engine library version and cannot be set by
     * publishers.
     */
    public static class Builder {

        private Instant             created = Instant.now();
        private String              configurationId;
        private JsonNode            attribution;
        private Audience            audience;
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
         * Sets the configuration id recorded in the manifest.
         *
         * @param configurationId
         * the configuration id
         *
         * @return this builder
         */
        public Builder configurationId(String configurationId) {
            this.configurationId = configurationId;
            return this;
        }

        /**
         * Sets the attribution value, an arbitrary JSON string or object that the
         * engine never interprets.
         *
         * @param attribution
         * the attribution JSON value
         *
         * @return this builder
         */
        public Builder attribution(JsonNode attribution) {
            this.attribution = attribution;
            return this;
        }

        /**
         * Sets the audience block naming the sealing recipient key id.
         *
         * @param sealingRecipient
         * the recipient key id, or null for no audience block
         *
         * @return this builder
         */
        public Builder audience(String sealingRecipient) {
            this.audience = sealingRecipient != null ? new Audience(sealingRecipient) : null;
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
         *
         * @throws BundleSignatureException
         * if a required field is missing or invalid
         */
        public BundleManifest buildUnsigned() {
            return validated(null);
        }

        /**
         * Builds the manifest with signature.
         *
         * @return signed manifest
         *
         * @throws BundleSignatureException
         * if a required field is missing or invalid
         */
        public BundleManifest build() {
            return validated(signature);
        }

        private BundleManifest validated(Signature manifestSignature) {
            val manifest = new BundleManifest(SaplVersion.VERSION, HASH_ALGORITHM, created, configurationId,
                    attribution, audience, new TreeMap<>(files), manifestSignature);
            validate(manifest);
            return manifest;
        }
    }
}
