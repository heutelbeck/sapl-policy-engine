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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Utility for loading PDP configurations from filesystem paths and raw content.
 * <p>
 * This loader handles the parsing of pdp.json configuration files and SAPL
 * policy documents. It also manages the
 * generation of configuration IDs for audit and correlation purposes.
 * </p>
 * <h2>Configuration ID Strategy</h2>
 * <p>
 * The configuration ID uniquely identifies a specific version of a PDP
 * configuration. This ID is available during
 * policy evaluation and enables correlation of authorization decisions with the
 * exact policy set that produced them.
 * </p>
 * <ul>
 * <li><b>Explicit ID:</b> If pdp.json contains a {@code configurationId} field,
 * that value is used. This is the
 * recommended approach for Policy Administration Points (PAPs) that manage
 * their own versioning.</li>
 * <li><b>Auto-generated ID:</b> If no explicit ID is provided, an ID is
 * generated from the source path, timestamp (for
 * mutable sources), and content hash.</li>
 * </ul>
 * <h2>Auto-generated ID Format</h2>
 * <ul>
 * <li>Directory sources: {@code dir:<path>@<timestamp>@sha256:<hash>}</li>
 * <li>Resource sources: {@code res:<path>@sha256:<hash>}</li>
 * </ul>
 * <p>
 * The hash component enables integrity verification - an auditor can recompute
 * the hash from policy files and verify it
 * matches the logged configuration ID.
 * </p>
 * <h2>Security: TOCTOU Mitigation</h2>
 * <p>
 * Directory loading validates size limits on actual file content, not pre-read
 * metadata. This prevents time-of-check to
 * time-of-use attacks where an attacker replaces a small file with a large one
 * between size check and content read.
 * </p>
 */
@Slf4j
@UtilityClass
public class PDPConfigurationLoader {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final int  MAX_FILE_COUNT           = 1000;
    private static final long MAX_TOTAL_SIZE_BYTES     = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE_MEGABYTES = MAX_TOTAL_SIZE_BYTES / (1024 * 1024);

    private static final String ERROR_BUNDLE_MISSING_CONFIGURATION_ID = "Bundle '%s' pdp.json is missing required field 'configurationId'.";
    private static final String ERROR_BUNDLE_MISSING_PDP_JSON         = "Bundle '%s' is missing pdp.json. Bundles require pdp.json with a configurationId.";
    private static final String ERROR_FAILED_TO_LIST_SAPL_FILES       = "Failed to list SAPL files in directory.";
    private static final String ERROR_FAILED_TO_PARSE_PDP_JSON        = "Failed to parse pdp.json content.";
    private static final String ERROR_FAILED_TO_READ_PDP_JSON         = "Failed to read pdp.json from '%s'.";
    private static final String ERROR_FAILED_TO_READ_SAPL_DOCUMENT    = "Failed to read SAPL document '%s'.";
    private static final String ERROR_FILE_COUNT_EXCEEDS_MAXIMUM      = "File count exceeds maximum of %d files.";
    private static final String ERROR_PDP_JSON_CONTENT_REQUIRED       = "pdp.json content must not be empty.";
    private static final String ERROR_PDP_JSON_FIRST_NOT_ALLOWED      = "FIRST is not allowed as combining algorithm at PDP level. It implies an ordering not present here.";
    private static final String ERROR_PDP_JSON_MISSING_ALGORITHM      = "pdp.json must contain an 'algorithm' field with votingMode, defaultDecision, and errorHandling.";
    private static final String ERROR_PDP_JSON_REQUIRED               = "pdp.json is required but not found at '%s'.";
    private static final String ERROR_SHA256_NOT_AVAILABLE            = "SHA-256 algorithm not available.";
    private static final String ERROR_TOTAL_SIZE_EXCEEDS_MAXIMUM      = "Total size of SAPL documents exceeds maximum of %d MB.";

    private static final JsonMapper MAPPER = createMapper();

    private static JsonMapper createMapper() {
        return JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    }

    /**
     * Loads a PDP configuration from a directory path.
     * <p>
     * Requires pdp.json to be present with an {@code algorithm} field.
     * If pdp.json contains a {@code configurationId}, it is used. Otherwise, an ID
     * is auto-generated in the format:
     * {@code dir:<path>@<timestamp>@sha256:<hash>}
     * </p>
     *
     * @param path
     * the directory containing pdp.json and *.sapl files
     * @param pdpId
     * the PDP identifier to use
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if loading fails
     */
    public static PDPConfiguration loadFromDirectory(Path path, String pdpId) {
        val pdpJsonPath  = path.resolve(PDP_JSON);
        val pdpJson      = loadPdpJson(pdpJsonPath);
        val saplContents = loadSaplDocumentsAsMap(path);
        val documents    = new ArrayList<>(saplContents.values());

        val configurationId = pdpJson.configurationId() != null ? pdpJson.configurationId()
                : generateDirectoryConfigurationId(path, pdpJson, saplContents);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), documents,
                new PdpData(pdpJson.variables(), pdpJson.secrets()));
    }

    /**
     * Loads a PDP configuration from raw content.
     * <p>
     * If pdpJsonContent contains a {@code configurationId}, it is used. Otherwise,
     * an ID is auto-generated in the
     * format: {@code res:<path>@sha256:<hash>}
     * </p>
     *
     * @param pdpJsonContent
     * the content of pdp.json (required, must contain algorithm)
     * @param saplDocuments
     * map of filename to SAPL document content
     * @param pdpId
     * the PDP identifier to use
     * @param sourcePath
     * the source path for auto-generated ID (e.g., classpath resource path)
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if loading fails
     */
    public static PDPConfiguration loadFromContent(String pdpJsonContent, Map<String, String> saplDocuments,
            String pdpId, String sourcePath) {
        val pdpJson   = parsePdpJson(pdpJsonContent);
        val documents = new ArrayList<>(saplDocuments.values());

        val configurationId = pdpJson.configurationId() != null ? pdpJson.configurationId()
                : generateResourceConfigurationId(sourcePath, pdpJson, saplDocuments);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), documents,
                new PdpData(pdpJson.variables(), pdpJson.secrets()));
    }

    /**
     * Loads a PDP configuration from raw content for bundles.
     * <p>
     * Bundles require pdp.json to be present with a {@code configurationId} field.
     * </p>
     *
     * @param pdpJsonContent
     * the content of pdp.json (required for bundles)
     * @param saplDocuments
     * map of filename to SAPL document content
     * @param pdpId
     * the PDP identifier to use
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if pdp.json is missing or configurationId is not specified
     */
    public static PDPConfiguration loadFromBundle(String pdpJsonContent, Map<String, String> saplDocuments,
            String pdpId) {
        if (pdpJsonContent == null || pdpJsonContent.isBlank()) {
            throw new PDPConfigurationException(ERROR_BUNDLE_MISSING_PDP_JSON.formatted(pdpId));
        }

        val pdpJson = parsePdpJson(pdpJsonContent);

        if (pdpJson.configurationId() == null || pdpJson.configurationId().isBlank()) {
            throw new PDPConfigurationException(ERROR_BUNDLE_MISSING_CONFIGURATION_ID.formatted(pdpId));
        }

        val documents = new ArrayList<>(saplDocuments.values());
        return new PDPConfiguration(pdpId, pdpJson.configurationId(), pdpJson.algorithm(), documents,
                new PdpData(pdpJson.variables(), pdpJson.secrets()));
    }

    private static String generateDirectoryConfigurationId(Path path, PdpJsonContent pdpJson,
            Map<String, String> saplContents) {
        val normalizedPath = normalizePath(path);
        val timestamp      = Instant.now().toString();
        val hash           = computeContentHash(pdpJson, saplContents);
        return "dir:%s@%s@sha256:%s".formatted(normalizedPath, timestamp, hash);
    }

    private static String generateResourceConfigurationId(String sourcePath, PdpJsonContent pdpJson,
            Map<String, String> saplContents) {
        val normalizedPath = normalizePath(sourcePath);
        val hash           = computeContentHash(pdpJson, saplContents);
        return "res:%s@sha256:%s".formatted(normalizedPath, hash);
    }

    private static String normalizePath(Path path) {
        var normalized = path.toAbsolutePath().normalize().toString();
        normalized = normalized.replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String path) {
        var normalized = path.replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private static String computeContentHash(PdpJsonContent pdpJson, Map<String, String> saplContents) {
        try {
            val digest  = MessageDigest.getInstance("SHA-256");
            val sorted  = new TreeMap<>(saplContents);
            val builder = new StringBuilder();

            builder.append("algorithm:").append(pdpJson.algorithm().toCanonicalString()).append('\n');

            for (val entry : sorted.entrySet()) {
                builder.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
            }

            val hashBytes = digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new PDPConfigurationException(ERROR_SHA256_NOT_AVAILABLE, e);
        }
    }

    private static PdpJsonContent loadPdpJson(Path pdpJsonPath) {
        if (!Files.exists(pdpJsonPath)) {
            throw new PDPConfigurationException(ERROR_PDP_JSON_REQUIRED.formatted(pdpJsonPath));
        }
        try {
            val content = Files.readString(pdpJsonPath, StandardCharsets.UTF_8);
            return parsePdpJson(content);
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_READ_PDP_JSON.formatted(pdpJsonPath), e);
        }
    }

    private static PdpJsonContent parsePdpJson(String content) {
        if (content == null || content.isBlank()) {
            throw new PDPConfigurationException(ERROR_PDP_JSON_CONTENT_REQUIRED);
        }
        try {
            val node = MAPPER.readTree(content);

            if (!node.has("algorithm")) {
                throw new PDPConfigurationException(ERROR_PDP_JSON_MISSING_ALGORITHM);
            }
            val algorithmNode = node.get("algorithm");
            if (algorithmNode.has("votingMode") && "FIRST".equals(algorithmNode.path("votingMode").asString())) {
                throw new PDPConfigurationException(ERROR_PDP_JSON_FIRST_NOT_ALLOWED);
            }
            val algorithm = MAPPER.treeToValue(algorithmNode, CombiningAlgorithm.class);

            String configurationId = null;
            if (node.has("configurationId")) {
                val idNode = node.get("configurationId");
                if (idNode.isString() && !idNode.asString().isBlank()) {
                    configurationId = idNode.asString();
                }
            }

            val variables = parseValueSection(node, "variables");
            val secrets   = parseValueSection(node, "secrets");

            return new PdpJsonContent(algorithm, configurationId, variables, secrets);
        } catch (JacksonException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_PARSE_PDP_JSON, e);
        }
    }

    private static ObjectValue parseValueSection(JsonNode node, String sectionName) throws JacksonException {
        val builder = ObjectValue.builder();
        if (node.has(sectionName)) {
            val sectionNode = node.get(sectionName);
            for (val property : sectionNode.properties()) {
                val value = MAPPER.treeToValue(property.getValue(), Value.class);
                builder.put(property.getKey(), value);
            }
        }
        return builder.build();
    }

    private static Map<String, String> loadSaplDocumentsAsMap(Path directory) {
        List<Path> saplPaths;
        try (Stream<Path> paths = Files.list(directory)) {
            saplPaths = paths.filter(p -> p.toString().endsWith(SAPL_EXTENSION)).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_LIST_SAPL_FILES, e);
        }

        if (saplPaths.size() > MAX_FILE_COUNT) {
            throw new PDPConfigurationException(ERROR_FILE_COUNT_EXCEEDS_MAXIMUM.formatted(MAX_FILE_COUNT));
        }

        // Read files and validate size atomically to prevent TOCTOU attacks.
        // An attacker could replace a small file with a large one between a
        // size check and the actual read.
        val documents = new HashMap<String, String>();
        var totalSize = 0L;
        for (val path : saplPaths) {
            val content = readSaplDocument(path);
            totalSize += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalSize > MAX_TOTAL_SIZE_BYTES) {
                throw new PDPConfigurationException(
                        ERROR_TOTAL_SIZE_EXCEEDS_MAXIMUM.formatted(MAX_TOTAL_SIZE_MEGABYTES));
            }
            val fileNamePath = path.getFileName();
            if (fileNamePath == null) {
                log.warn("Skipping SAPL document with no filename: {}.", path);
                continue;
            }
            documents.put(fileNamePath.toString(), content);
        }
        return documents;
    }

    private static String readSaplDocument(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_READ_SAPL_DOCUMENT.formatted(path.getFileName()), e);
        }
    }

    private record PdpJsonContent(
            CombiningAlgorithm algorithm,
            String configurationId,
            ObjectValue variables,
            ObjectValue secrets) {}

}
