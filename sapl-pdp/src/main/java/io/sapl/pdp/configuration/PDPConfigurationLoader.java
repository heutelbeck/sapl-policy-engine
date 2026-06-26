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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.compiler.expressions.CompilationContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
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
 * generated from the source path and, for mutable directory sources, a load
 * timestamp. It is a disambiguator, not an integrity token.</li>
 * </ul>
 * <h2>Auto-generated ID Format</h2>
 * <ul>
 * <li>Directory sources: {@code dir:<path>@<timestamp>}</li>
 * <li>Resource sources: {@code res:<path>}</li>
 * </ul>
 * <p>
 * The auto-generated ID does not carry a content hash and must not be treated
 * as an integrity guarantee. It does not cover variables, secrets, or compiler
 * options, so two materially different configurations may share an
 * auto-generated ID. Audit-grade integrity comes from signed bundles, which
 * supply their own {@code configurationId}, not from auto-generated IDs.
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

    private static final String FIELD_COMPILER_OPTIONS = "compilerOptions";
    private static final String PDP_JSON               = "pdp.json";
    private static final String SAPL_EXTENSION         = ".sapl";

    // Internal cap on total SAPL bytes per directory load, defaulting to 1 GiB.
    // It is not a published compiler option.
    private static final String OPTION_MAX_TOTAL_SIZE_MEGABYTES  = "maxTotalSizeMegabytes";
    private static final int    DEFAULT_MAX_TOTAL_SIZE_MEGABYTES = 1024;

    private static final String ERROR_BUNDLE_MISSING_CONFIGURATION_ID = "Bundle '%s' pdp.json is missing required field 'configurationId'.";
    private static final String ERROR_BUNDLE_MISSING_PDP_JSON         = "Bundle '%s' is missing pdp.json. Bundles require pdp.json with a configurationId.";
    private static final String ERROR_FAILED_TO_LIST_SAPL_FILES       = "Failed to list SAPL files in directory.";
    private static final String ERROR_FAILED_TO_PARSE_PDP_JSON        = "Failed to parse pdp.json content.";
    private static final String ERROR_FAILED_TO_READ_PDP_JSON         = "Failed to read pdp.json from '%s'.";
    private static final String ERROR_FAILED_TO_READ_SAPL_DOCUMENT    = "Failed to read SAPL document '%s'.";
    private static final String ERROR_FILE_COUNT_EXCEEDS_MAXIMUM      = "File count exceeds maximum of %d files.";
    private static final String ERROR_PDP_JSON_CONTENT_REQUIRED       = "pdp.json content must not be empty.";
    private static final String ERROR_PDP_JSON_FIRST_NOT_ALLOWED      = "FIRST is not allowed as combining algorithm at PDP level. It implies an ordering not present here.";

    private static final String WARN_PDP_JSON_MISSING_ALGORITHM  = "pdp.json does not contain an 'algorithm' field. Using default: {}.";
    private static final String WARN_PDP_JSON_NOT_FOUND          = "pdp.json not found at '{}'. Using defaults: algorithm={}, configurationId=default.";
    private static final String ERROR_TOTAL_SIZE_EXCEEDS_MAXIMUM = "Total size of SAPL documents exceeds maximum of %d MB.";

    private static final JsonMapper MAPPER = createMapper();

    private static JsonMapper createMapper() {
        return JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_YAML_COMMENTS)
                .addModule(new SaplJacksonModule()).build();
    }

    /**
     * Loads a PDP configuration from a directory path.
     * <p>
     * If pdp.json is present, its {@code algorithm} and optional
     * {@code configurationId} are used. If pdp.json is absent, safe defaults
     * are applied ({@link CombiningAlgorithm#DEFAULT}, configurationId "default").
     * If pdp.json is present but has no {@code algorithm} field, the default
     * algorithm is used with a warning.
     * </p>
     * <p>
     * When no explicit {@code configurationId} is provided, an ID is
     * auto-generated in the format:
     * {@code dir:<path>@<timestamp>}
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
        val pdpJsonPath           = path.resolve(PDP_JSON);
        val pdpJson               = loadPdpJson(pdpJsonPath);
        val maxDocuments          = CompilationContext.intOption(pdpJson.compilerOptions(),
                CompilationContext.OPTION_MAX_POLICY_DOCUMENTS, CompilationContext.DEFAULT_MAX_POLICY_DOCUMENTS);
        val maxTotalSizeMegabytes = CompilationContext.intOption(pdpJson.compilerOptions(),
                OPTION_MAX_TOTAL_SIZE_MEGABYTES, DEFAULT_MAX_TOTAL_SIZE_MEGABYTES);
        val saplContents          = loadSaplDocumentsAsMap(path, maxDocuments, maxTotalSizeMegabytes);
        val documents             = new ArrayList<>(saplContents.values());

        val configurationId = pdpJson.configurationId() != null ? pdpJson.configurationId()
                : generateDirectoryConfigurationId(path);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), pdpJson.compilerOptions(), documents,
                new PdpData(pdpJson.variables(), pdpJson.secrets()));
    }

    /**
     * Loads a PDP configuration from raw content.
     * <p>
     * If pdpJsonContent contains a {@code configurationId}, it is used. Otherwise,
     * an ID is auto-generated in the
     * format: {@code res:<path>}
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
                : generateResourceConfigurationId(sourcePath);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), pdpJson.compilerOptions(), documents,
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
        return new PDPConfiguration(pdpId, pdpJson.configurationId(), pdpJson.algorithm(), pdpJson.compilerOptions(),
                documents, new PdpData(pdpJson.variables(), pdpJson.secrets()));
    }

    private static String generateDirectoryConfigurationId(Path path) {
        val normalizedPath = normalizePath(path);
        val timestamp      = Instant.now().toString();
        return "dir:%s@%s".formatted(normalizedPath, timestamp);
    }

    private static String generateResourceConfigurationId(String sourcePath) {
        return "res:%s".formatted(normalizePath(sourcePath));
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

    private static PdpJsonContent loadPdpJson(Path pdpJsonPath) {
        if (!Files.exists(pdpJsonPath)) {
            log.warn(WARN_PDP_JSON_NOT_FOUND, pdpJsonPath, CombiningAlgorithm.DEFAULT.toCanonicalString());
            return new PdpJsonContent(CombiningAlgorithm.DEFAULT, "default", Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
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

            CombiningAlgorithm algorithm;
            val                algorithmNode = node.get("algorithm");
            // A present-but-null algorithm degrades to the default, exactly like an absent
            // field, rather than yielding a null algorithm that crashes compilation.
            if (algorithmNode == null || algorithmNode.isNull()) {
                log.warn(WARN_PDP_JSON_MISSING_ALGORITHM, CombiningAlgorithm.DEFAULT.toCanonicalString());
                algorithm = CombiningAlgorithm.DEFAULT;
            } else {
                if (algorithmNode.has("votingMode") && "FIRST".equals(algorithmNode.path("votingMode").asString())) {
                    throw new PDPConfigurationException(ERROR_PDP_JSON_FIRST_NOT_ALLOWED);
                }
                algorithm = MAPPER.treeToValue(algorithmNode, CombiningAlgorithm.class);
            }

            String configurationId = null;
            if (node.has("configurationId")) {
                val idNode = node.get("configurationId");
                if (idNode.isString() && !idNode.asString().isBlank()) {
                    configurationId = idNode.asString();
                }
            }

            val compilerOptions = parseCompilerOptions(node);

            val variables = parseValueSection(node, "variables");
            val secrets   = parseValueSection(node, "secrets");

            return new PdpJsonContent(algorithm, compilerOptions, configurationId, variables, secrets);
        } catch (JacksonException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_PARSE_PDP_JSON, e);
        }
    }

    private static ObjectValue parseCompilerOptions(JsonNode node) {
        if (node.has(FIELD_COMPILER_OPTIONS)) {
            return parseValueSection(node, FIELD_COMPILER_OPTIONS);
        }
        if (node.has("compilerFlags")) {
            return parseValueSection(node, "compilerFlags");
        }
        return Value.EMPTY_OBJECT;
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

    private static Map<String, String> loadSaplDocumentsAsMap(Path directory, int maxFileCount,
            int maxTotalSizeMegabytes) {
        List<Path> saplPaths;
        try (Stream<Path> paths = Files.list(directory)) {
            saplPaths = paths.filter(p -> p.toString().endsWith(SAPL_EXTENSION)).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_LIST_SAPL_FILES, e);
        }

        if (saplPaths.size() > maxFileCount) {
            throw new PDPConfigurationException(ERROR_FILE_COUNT_EXCEEDS_MAXIMUM.formatted(maxFileCount));
        }

        // Read files and validate size atomically to prevent TOCTOU attacks.
        // An attacker could replace a small file with a large one between a
        // size check and the actual read.
        val documents         = new HashMap<String, String>();
        val maxTotalSizeBytes = maxTotalSizeMegabytes * 1024L * 1024;
        var totalSize         = 0L;
        for (val path : saplPaths) {
            val content = readSaplDocument(path);
            totalSize += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalSize > maxTotalSizeBytes) {
                throw new PDPConfigurationException(ERROR_TOTAL_SIZE_EXCEEDS_MAXIMUM.formatted(maxTotalSizeMegabytes));
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
            ObjectValue compilerOptions,
            String configurationId,
            ObjectValue variables,
            ObjectValue secrets) {

        PdpJsonContent(CombiningAlgorithm algorithm,
                String configurationId,
                ObjectValue variables,
                ObjectValue secrets) {
            this(algorithm, Value.EMPTY_OBJECT, configurationId, variables, secrets);
        }
    }

}
