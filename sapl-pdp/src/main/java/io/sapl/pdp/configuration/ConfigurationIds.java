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
package io.sapl.pdp.configuration;

import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import lombok.val;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * The single content-derivation function for configuration ids and the id
 * validity rule shared by the bundle builder, the bundle manifest, and the
 * command line tooling.
 * <p>
 * A derived id has the form {@code <label>@<hash16>}, where the hash is the
 * 16-hex-character prefix of a SHA-256 digest over the name-sorted,
 * length-prefixed (name, content) pairs of the source. Length prefixing makes
 * the encoding injective, so shifting bytes between names and contents always
 * changes the id. Identical content always yields the identical id; the id is
 * recomputed on every load and never persisted.
 * </p>
 * <p>
 * A valid configuration id is 1 to {@value #MAX_CONFIGURATION_ID_LENGTH}
 * printable ASCII characters without whitespace and without the separators
 * {@code /} and {@code \}.
 * </p>
 */
public final class ConfigurationIds {

    /** Maximum length of a configuration id in characters. */
    public static final int MAX_CONFIGURATION_ID_LENGTH = 256;

    /** Human-readable statement of the configuration id validity rule. */
    public static final String VALIDITY_RULE = "A configuration id must be 1 to " + MAX_CONFIGURATION_ID_LENGTH
            + " printable ASCII characters without whitespace, '/' or '\\'.";

    private static final String HASH_ALGORITHM    = "SHA-256";
    private static final int    HASH_PREFIX_CHARS = 16;

    private static final String ERROR_CONFIGURATION_ID_INVALID = "Configuration id '%s' is invalid. " + VALIDITY_RULE;

    private ConfigurationIds() {
    }

    /**
     * The canonical entry set of a complete assembled configuration: the
     * closure authority for content-derived ids. Includes every component
     * that influences PDP behavior - combining algorithm, compiler options,
     * variables, secrets, all documents as a sorted multiset (PDP-level
     * voting modes are order-independent), extensions, extension secrets,
     * and the critical extension set. The pdpId and configurationId are
     * excluded: they are instance and publication identity, never content.
     * No call site may hand-roll its own entry assembly.
     *
     * @param configuration the assembled configuration
     * @return the canonical, name-sorted entry set for {@link #derive}
     */
    public static Map<String, byte[]> entriesOf(PDPConfiguration configuration) {
        val entries = new TreeMap<String, byte[]>();
        entries.put("algorithm", utf8(configuration.combiningAlgorithm().toCanonicalString()));
        entries.put("compilerOptions", utf8(ValueJsonMarshaller.toJsonString(configuration.compilerOptions())));
        entries.put("variables", utf8(ValueJsonMarshaller.toJsonString(configuration.data().variables())));
        entries.put("secrets", utf8(ValueJsonMarshaller.toJsonString(configuration.data().secrets())));
        val sortedDocuments = configuration.saplDocuments().stream().sorted().toList();
        for (var i = 0; i < sortedDocuments.size(); i++) {
            entries.put("document-%05d".formatted(i), utf8(sortedDocuments.get(i)));
        }
        configuration.extensions().forEach(
                (name, value) -> entries.put("extension:" + name, utf8(ValueJsonMarshaller.toJsonString(value))));
        configuration.extensionSecrets().forEach((name, value) -> entries.put("extension-secrets:" + name,
                utf8(ValueJsonMarshaller.toJsonString(value))));
        entries.put("criticalExtensions",
                utf8(String.join("\n", configuration.criticalExtensions().stream().sorted().toList())));
        return entries;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Derives a configuration id of the form {@code <label>@<hash16>} from named
     * contents.
     *
     * @param label a short source label, for example {@code bundle} or
     * {@code dir:policies}
     * @param namedContents map of entry name to entry content bytes
     * @return the derived configuration id
     */
    public static String derive(String label, Map<String, byte[]> namedContents) {
        return label + "@" + contentHash16(namedContents);
    }

    /**
     * Computes the 16-hex-character content hash over the name-sorted,
     * length-prefixed (name, content) pairs.
     *
     * @param namedContents map of entry name to entry content bytes
     * @return the first 16 lowercase hex characters of the SHA-256 digest
     */
    public static String contentHash16(Map<String, byte[]> namedContents) {
        val sorted = new TreeMap<>(namedContents);
        val digest = sha256();
        for (val entry : sorted.entrySet()) {
            val nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            digest.update(lengthPrefix(nameBytes.length));
            digest.update(nameBytes);
            digest.update(lengthPrefix(entry.getValue().length));
            digest.update(entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, HASH_PREFIX_CHARS);
    }

    /**
     * Checks whether a configuration id is valid: 1 to 256 printable ASCII
     * characters without whitespace, {@code /} or {@code \}.
     *
     * @param configurationId the id to check
     * @return true if the id is valid
     */
    public static boolean isValid(String configurationId) {
        if (configurationId == null || configurationId.isEmpty()
                || configurationId.length() > MAX_CONFIGURATION_ID_LENGTH) {
            return false;
        }
        for (var i = 0; i < configurationId.length(); i++) {
            val character = configurationId.charAt(i);
            if (character < 0x21 || character > 0x7E || character == '/' || character == '\\') {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates a configuration id, failing fast on invalid input.
     *
     * @param configurationId the id to validate
     * @throws IllegalArgumentException if the id is invalid
     */
    public static void requireValid(String configurationId) {
        if (!isValid(configurationId)) {
            throw new IllegalArgumentException(ERROR_CONFIGURATION_ID_INVALID.formatted(configurationId));
        }
    }

    private static byte[] lengthPrefix(long length) {
        return ByteBuffer.allocate(Long.BYTES).putLong(length).array();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " not available.", e);
        }
    }
}
