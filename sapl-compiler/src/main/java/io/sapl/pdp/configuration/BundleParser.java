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
package io.sapl.pdp.configuration;

import io.sapl.api.pdp.PDPConfiguration;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parser for SAPL bundle files (.saplbundle).
 * <p>
 * A SAPL bundle is a ZIP archive containing policy documents and an optional
 * pdp.json configuration file. This parser
 * extracts bundle contents from various input sources while enforcing security
 * constraints.
 * </p>
 * <h2>Bundle Structure</h2>
 *
 * <pre>
 * my-policies.saplbundle (ZIP archive):
 * ├── pdp.json           (optional configuration)
 * ├── access-control.sapl
 * ├── audit.sapl
 * └── logging.sapl
 * </pre>
 * <p>
 * Subdirectories inside bundles are ignored. Only root-level files are
 * processed.
 * </p>
 * <h2>Security</h2>
 * <p>
 * This parser enforces multiple layers of protection against malicious
 * archives:
 * </p>
 * <ul>
 * <li><b>ZIP bomb protection:</b> Limits on uncompressed size (10 MB),
 * compression ratio (100:1), entry count (1000),
 * and entry name length (255 characters).</li>
 * <li><b>Path traversal prevention:</b> Entries containing "..", or starting
 * with "/" or "\" are rejected.</li>
 * <li><b>Nested archive rejection:</b> Entries ending in .zip, .jar, .war, or
 * .saplbundle are rejected to prevent
 * recursive decompression attacks.</li>
 * </ul>
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // From file
 * PDPConfiguration config = BundleParser.parse(bundlePath, "production", "v1.0");
 *
 * // From HTTP upload
 * PDPConfiguration config = BundleParser.parse(request.getInputStream(), "production", "v1.0");
 *
 * // From byte array (e.g., from database or message queue)
 * PDPConfiguration config = BundleParser.parse(bundleBytes, "production", "v1.0");
 * }</pre>
 */
@UtilityClass
public class BundleParser {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final Set<String> NESTED_ARCHIVE_EXTENSIONS = Set.of(".zip", ".saplbundle", ".jar", ".war");

    // ZIP bomb protection limits
    private static final long   MAX_UNCOMPRESSED_SIZE_BYTES = 10L * 1024 * 1024;
    private static final long   MAX_UNCOMPRESSED_SIZE_MB    = MAX_UNCOMPRESSED_SIZE_BYTES / 1024 / 1024;
    private static final double MAX_COMPRESSION_RATIO       = 100.0;
    private static final int    MAX_ENTRY_COUNT             = 1000;
    private static final int    MAX_ENTRY_NAME_LENGTH       = 255;

    private static final int READ_BUFFER_SIZE = 4096;

    /**
     * Parses a bundle from a filesystem path.
     *
     * @param bundlePath
     * the path to the .saplbundle file
     * @param pdpId
     * the PDP identifier
     * @param configurationId
     * the configuration version identifier
     *
     * @return the PDP configuration
     *
     * @throws PDPConfigurationException
     * if parsing fails or security constraints are violated
     */
    public PDPConfiguration parse(Path bundlePath, String pdpId, String configurationId) {
        try {
            val compressedSize = Files.size(bundlePath);
            try (val inputStream = Files.newInputStream(bundlePath)) {
                return parseInternal(inputStream, compressedSize, bundlePath.toString()).toPDPConfiguration(pdpId,
                        configurationId);
            }
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to read bundle file.", e);
        }
    }

    /**
     * Parses a bundle from an input stream.
     * <p>
     * When the compressed size is unknown, compression ratio validation is skipped
     * but all other security checks remain
     * active.
     * </p>
     *
     * @param inputStream
     * the input stream containing bundle data
     * @param pdpId
     * the PDP identifier
     * @param configurationId
     * the configuration version identifier
     *
     * @return the PDP configuration
     *
     * @throws PDPConfigurationException
     * if parsing fails or security constraints are violated
     */
    public PDPConfiguration parse(InputStream inputStream, String pdpId, String configurationId) {
        return parseInternal(inputStream, -1, "stream").toPDPConfiguration(pdpId, configurationId);
    }

    /**
     * Parses a bundle from an input stream with known compressed size.
     * <p>
     * Providing the compressed size enables compression ratio validation for better
     * ZIP bomb protection.
     * </p>
     *
     * @param inputStream
     * the input stream containing bundle data
     * @param compressedSize
     * the size of the compressed data in bytes
     * @param pdpId
     * the PDP identifier
     * @param configurationId
     * the configuration version identifier
     *
     * @return the PDP configuration
     *
     * @throws PDPConfigurationException
     * if parsing fails or security constraints are violated
     */
    public PDPConfiguration parse(InputStream inputStream, long compressedSize, String pdpId, String configurationId) {
        return parseInternal(inputStream, compressedSize, "stream").toPDPConfiguration(pdpId, configurationId);
    }

    /**
     * Parses a bundle from a byte array.
     *
     * @param bundleBytes
     * the bundle data as byte array
     * @param pdpId
     * the PDP identifier
     * @param configurationId
     * the configuration version identifier
     *
     * @return the PDP configuration
     *
     * @throws PDPConfigurationException
     * if parsing fails or security constraints are violated
     */
    public PDPConfiguration parse(byte[] bundleBytes, String pdpId, String configurationId) {
        return parseInternal(new ByteArrayInputStream(bundleBytes), bundleBytes.length, "byte array")
                .toPDPConfiguration(pdpId, configurationId);
    }

    private BundleContent parseInternal(InputStream inputStream, long compressedSize, String sourceDescription) {
        val content           = new HashMap<String, String>();
        var totalUncompressed = 0L;
        var entryCount        = 0;

        try (val zipStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                entryCount++;
                validateZipEntry(entry, entryCount, sourceDescription);

                val entryName = normalizeEntryName(entry.getName());

                if (isSkippableEntry(entry, entryName)) {
                    continue;
                }

                val entryContent = readZipEntryContent(zipStream, sourceDescription, totalUncompressed, compressedSize);
                totalUncompressed += entryContent.length();

                if (compressedSize > 0) {
                    validateCompressionRatio(compressedSize, totalUncompressed, sourceDescription);
                }

                content.put(entryName, entryContent);
            }
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to parse bundle from %s.".formatted(sourceDescription), e);
        }

        return toBundleContent(content);
    }

    private BundleContent toBundleContent(HashMap<String, String> content) {
        val pdpJson   = content.remove(PDP_JSON);
        val saplFiles = new HashMap<String, String>();

        for (val entry : content.entrySet()) {
            if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                saplFiles.put(entry.getKey(), entry.getValue());
            }
        }

        return new BundleContent(pdpJson, saplFiles);
    }

    private boolean isSkippableEntry(ZipEntry entry, String normalizedName) {
        if (entry.isDirectory()) {
            return true;
        }
        return normalizedName.contains("/");
    }

    private void validateZipEntry(ZipEntry entry, int entryCount, String sourceDescription) {
        if (entryCount > MAX_ENTRY_COUNT) {
            throw zipBombException("Too many entries (>%d).".formatted(MAX_ENTRY_COUNT), sourceDescription);
        }

        val entryName = entry.getName();

        if (entryName.length() > MAX_ENTRY_NAME_LENGTH) {
            throw zipBombException("Entry name too long (>%d).".formatted(MAX_ENTRY_NAME_LENGTH), sourceDescription);
        }

        if (isNestedArchive(entryName)) {
            throw zipBombException("Nested archive detected.", sourceDescription);
        }

        if (isPathTraversalAttempt(entryName)) {
            throw new PDPConfigurationException(
                    "ZIP security violation: Path traversal attempt in bundle from %s.".formatted(sourceDescription));
        }
    }

    private boolean isNestedArchive(String entryName) {
        val lowerName = entryName.toLowerCase();
        return NESTED_ARCHIVE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private boolean isPathTraversalAttempt(String entryName) {
        return entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\");
    }

    private PDPConfigurationException zipBombException(String reason, String sourceDescription) {
        return new PDPConfigurationException("ZIP bomb detected: %s Source: %s.".formatted(reason, sourceDescription));
    }

    private String normalizeEntryName(String name) {
        var normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String readZipEntryContent(ZipInputStream zipStream, String sourceDescription, long currentTotal,
            long compressedSize) throws IOException {
        val buffer    = new ByteArrayOutputStream();
        val data      = new byte[READ_BUFFER_SIZE];
        var entrySize = 0L;
        int bytesRead;

        while ((bytesRead = zipStream.read(data)) != -1) {
            entrySize += bytesRead;
            val runningTotal = currentTotal + entrySize;

            validateUncompressedSize(runningTotal, sourceDescription);

            if (compressedSize > 0) {
                validateCompressionRatioDuringRead(compressedSize, runningTotal, sourceDescription);
            }

            buffer.write(data, 0, bytesRead);
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void validateUncompressedSize(long totalUncompressed, String sourceDescription) {
        if (totalUncompressed > MAX_UNCOMPRESSED_SIZE_BYTES) {
            throw zipBombException("Uncompressed size exceeds %d MB.".formatted(MAX_UNCOMPRESSED_SIZE_MB),
                    sourceDescription);
        }
    }

    private void validateCompressionRatioDuringRead(long compressedSize, long uncompressedSize,
            String sourceDescription) {
        if (exceedsCompressionRatio(compressedSize, uncompressedSize)) {
            throw zipBombException("Compression ratio exceeds %d:1.".formatted((int) MAX_COMPRESSION_RATIO),
                    sourceDescription);
        }
    }

    private void validateCompressionRatio(long compressedSize, long uncompressedSize, String sourceDescription) {
        if (exceedsCompressionRatio(compressedSize, uncompressedSize)) {
            val ratio = (double) uncompressedSize / compressedSize;
            throw zipBombException(
                    "Compression ratio %.1f:1 exceeds maximum %d:1.".formatted(ratio, (int) MAX_COMPRESSION_RATIO),
                    sourceDescription);
        }
    }

    private boolean exceedsCompressionRatio(long compressedSize, long uncompressedSize) {
        return (double) uncompressedSize / compressedSize > MAX_COMPRESSION_RATIO;
    }

    /** Intermediate representation of extracted bundle content. */
    private record BundleContent(String pdpJson, Map<String, String> saplDocuments) {

        PDPConfiguration toPDPConfiguration(String pdpId, String configurationId) {
            return PDPConfigurationLoader.loadFromContent(pdpJson, saplDocuments, pdpId, configurationId);
        }
    }
}
