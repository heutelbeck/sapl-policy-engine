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
import io.sapl.secrets.ValueSealer;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility for loading PDP configurations from filesystem paths and raw content.
 * <p>
 * This loader handles the parsing of pdp.json configuration files and SAPL
 * policy documents. It also derives configuration IDs for audit and
 * correlation purposes.
 * </p>
 * <h2>Configuration ID Strategy</h2>
 * <p>
 * The configuration ID uniquely identifies a specific publication of a PDP
 * configuration. This ID is available during policy evaluation and enables
 * correlation of authorization decisions with the exact policy set that
 * produced them. Explicit IDs exist only in bundle manifests; pdp.json never
 * carries a configuration ID, and a pdp.json still containing one is rejected.
 * </p>
 * <ul>
 * <li><b>Bundle sources:</b> The ID is read from the bundle manifest and
 * supplied by the caller of {@link #loadFromBundle}.</li>
 * <li><b>Directory sources:</b> The ID is derived from content on every
 * (re)load as {@code dir:<dirName>@<hash16>}, where the hash covers every file
 * the loader reads (pdp.json, SAPL documents, secrets, and extension
 * files).</li>
 * <li><b>Resource sources:</b> The ID is derived the same way as
 * {@code res:<name>@<hash16>}.</li>
 * </ul>
 * <p>
 * Derived IDs are recomputed on every load and never persisted; identical
 * content always yields the identical ID.
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
    private static final String SECRETS_SECTION        = "secrets";

    // Internal cap on total SAPL bytes per directory load, defaulting to 1 GiB.
    // It is not a published compiler option.
    private static final String OPTION_MAX_TOTAL_SIZE_MEGABYTES  = "maxTotalSizeMegabytes";
    private static final int    DEFAULT_MAX_TOTAL_SIZE_MEGABYTES = 1024;
    private static final int    MAX_PDP_JSON_SIZE_MEBIBYTES      = 1024;
    private static final long   MAX_PDP_JSON_BYTES               = MAX_PDP_JSON_SIZE_MEBIBYTES * 1024L * 1024L;
    private static final int    READ_BUFFER_SIZE                 = 8192;

    private static final String ERROR_BUNDLE_CONFIGURATION_ID_REQUIRED  = "Bundle '%s' requires a non-blank configurationId from the bundle manifest.";
    private static final String ERROR_BUNDLE_MISSING_PDP_JSON           = "Bundle '%s' is missing pdp.json. Bundles require a pdp.json configuration file.";
    private static final String ERROR_CONFIG_FILE_SIZE_EXCEEDS_MAXIMUM  = "%s exceeds maximum size of %d MiB.";
    private static final String ERROR_FAILED_TO_LIST_EXTENSION_FILES    = "Failed to list extension files in directory.";
    private static final String ERROR_FAILED_TO_LIST_SAPL_FILES         = "Failed to list SAPL files in directory.";
    private static final String ERROR_FAILED_TO_PARSE_PDP_JSON          = "Failed to parse pdp.json content.";
    private static final String ERROR_FAILED_TO_READ_EXTENSION_FILE     = "Failed to read extension file '%s'.";
    private static final String ERROR_FAILED_TO_READ_PDP_JSON           = "Failed to read pdp.json from '%s'.";
    private static final String ERROR_FAILED_TO_READ_SAPL_DOCUMENT      = "Failed to read SAPL document '%s'.";
    private static final String ERROR_FILE_COUNT_EXCEEDS_MAXIMUM        = "File count exceeds maximum of %d files.";
    private static final String ERROR_MIXED_SEALING_IN_DIRECTORY        = "The directory mixes sealed and plaintext secrets files. Seal all secrets or none.";
    private static final String ERROR_PDP_JSON_CONFIGURATION_ID_REMOVED = "pdp.json must not contain a 'configurationId' field. The configurationId moved to the bundle manifest and is derived from content for directory and resource sources. Remove the field.";
    private static final String ERROR_PDP_JSON_CONTENT_REQUIRED         = "pdp.json content must not be empty.";
    private static final String ERROR_PDP_JSON_FIRST_NOT_ALLOWED        = "FIRST is not allowed as combining algorithm at PDP level. It implies an ordering not present here.";
    private static final String ERROR_PDP_JSON_SECRETS_NOT_ALLOWED      = "pdp.json must not contain a 'secrets' section. Move the secrets object to 'secrets.json' (sealed: 'secrets.sealed.json').";
    private static final String ERROR_SEALED_CONTENT_NOT_SEALED         = "File '%s' is named sealed but its content is not sealed.";
    private static final String ERROR_SECRETS_FILE_NOT_OBJECT           = "Secrets file '%s' must contain a JSON object.";
    private static final String ERROR_TOTAL_SIZE_EXCEEDS_MAXIMUM        = "Total size of SAPL documents exceeds maximum of %d MB.";

    private static final String WARN_PDP_JSON_MISSING_ALGORITHM = "pdp.json does not contain an 'algorithm' field. Using default: {}.";
    private static final String WARN_PDP_JSON_NOT_FOUND         = "pdp.json not found at '{}'. Using default algorithm: {}.";

    private static final JsonMapper MAPPER = createMapper();

    private static JsonMapper createMapper() {
        return JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_YAML_COMMENTS)
                .addModule(new SaplJacksonModule()).build();
    }

    /**
     * Loads a PDP configuration from a directory path.
     * <p>
     * If pdp.json is present, its {@code algorithm} is used. If pdp.json is
     * absent, the default algorithm {@link CombiningAlgorithm#DEFAULT} is
     * applied. If pdp.json is present but has no {@code algorithm} field, the
     * default algorithm is used with a warning.
     * </p>
     * <p>
     * The configuration ID is derived from the directory content on every
     * (re)load in the format {@code dir:<dirName>@<hash16>}, covering every file
     * this loader reads.
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
        val pdpJsonFile           = loadPdpJson(pdpJsonPath);
        val pdpJson               = pdpJsonFile.parsed();
        val maxDocuments          = CompilationContext.intOption(pdpJson.compilerOptions(),
                CompilationContext.OPTION_MAX_POLICY_DOCUMENTS, CompilationContext.DEFAULT_MAX_POLICY_DOCUMENTS);
        val maxTotalSizeMegabytes = CompilationContext.intOption(pdpJson.compilerOptions(),
                OPTION_MAX_TOTAL_SIZE_MEGABYTES, DEFAULT_MAX_TOTAL_SIZE_MEGABYTES);
        val saplContents          = loadSaplDocumentsAsMap(path, maxDocuments, maxTotalSizeMegabytes);
        val documents             = new ArrayList<>(saplContents.values());
        val supplements           = loadSupplementsFromDirectory(path);

        val contents = new TreeMap<String, byte[]>();
        if (pdpJsonFile.raw() != null) {
            contents.put(PDP_JSON, pdpJsonFile.raw().getBytes(StandardCharsets.UTF_8));
        }
        putUtf8(contents, saplContents);
        putUtf8(contents, supplements.rawFiles());
        val configurationId = ConfigurationIds.derive("dir:" + directoryName(path), contents);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), pdpJson.compilerOptions(), documents,
                new PdpData(pdpJson.variables(), supplements.secrets()), supplements.extensions(),
                supplements.extensionSecrets(), supplements.criticalExtensions());
    }

    private static void putUtf8(Map<String, byte[]> contents, Map<String, String> textByName) {
        for (val entry : textByName.entrySet()) {
            contents.put(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String directoryName(Path path) {
        val fileName = path.toAbsolutePath().normalize().getFileName();
        return fileName != null ? fileName.toString() : "root";
    }

    private static DirectorySupplements loadSupplementsFromDirectory(Path directory) {
        val    extensions       = new LinkedHashMap<String, Value>();
        val    extensionSecrets = new LinkedHashMap<String, Value>();
        val    rawFiles         = new LinkedHashMap<String, String>();
        var    secrets          = Value.EMPTY_OBJECT;
        var    hasPlaintext     = false;
        var    hasSealed        = false;
        String criticalJson     = null;

        List<Path> files;
        try (Stream<Path> paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_LIST_EXTENSION_FILES, e);
        }

        for (val path : files) {
            val fileNamePath = path.getFileName();
            if (fileNamePath == null) {
                continue;
            }
            val name = fileNamePath.toString();
            if (ExtensionFiles.CRITICAL_EXTENSIONS_FILE.equals(name)) {
                criticalJson = readExtensionFileContent(path);
                rawFiles.put(name, criticalJson);
            } else if (ExtensionFiles.SECRETS_FILE.equals(name)) {
                hasPlaintext = true;
                val raw = readExtensionFileContent(path);
                rawFiles.put(name, raw);
                secrets = parseSecretsObject(name, raw);
            } else if (ExtensionFiles.SEALED_SECRETS_FILE.equals(name)) {
                hasSealed = true;
                val raw = readExtensionFileContent(path);
                rawFiles.put(name, raw);
                secrets = parseSecretsObject(name, raw);
                requireSealedContent(name, secrets);
            } else if (ExtensionFiles.isSealedExtensionSecretsFile(name)) {
                hasSealed = true;
                val raw = readExtensionFileContent(path);
                rawFiles.put(name, raw);
                val sealedValue = Value.ofJson(raw);
                requireSealedContent(name, sealedValue);
                extensionSecrets.put(ExtensionFiles.sealedExtensionSecretsNameOf(name), sealedValue);
            } else if (ExtensionFiles.isExtensionSecretsFile(name)) {
                hasPlaintext = true;
                val raw = readExtensionFileContent(path);
                rawFiles.put(name, raw);
                extensionSecrets.put(ExtensionFiles.extensionSecretsNameOf(name), Value.ofJson(raw));
            } else if (ExtensionFiles.isExtensionFile(name)) {
                val raw = readExtensionFileContent(path);
                rawFiles.put(name, raw);
                extensions.put(ExtensionFiles.extensionNameOf(name), Value.ofJson(raw));
            }
        }

        if (hasPlaintext && hasSealed) {
            throw new PDPConfigurationException(ERROR_MIXED_SEALING_IN_DIRECTORY);
        }

        val criticalExtensions = ExtensionFiles.parseCriticalExtensions(criticalJson);
        ExtensionFiles.validateIntegrity(criticalExtensions, extensions.keySet(), extensionSecrets.keySet());
        return new DirectorySupplements(secrets, extensions, extensionSecrets, criticalExtensions, rawFiles);
    }

    private static ObjectValue parseSecretsObject(String fileName, String content) {
        if (Value.ofJson(content) instanceof ObjectValue secrets) {
            return secrets;
        }
        throw new PDPConfigurationException(ERROR_SECRETS_FILE_NOT_OBJECT.formatted(fileName));
    }

    private static void requireSealedContent(String fileName, Value value) {
        if (!ValueSealer.hasSealedShape(value)) {
            throw new PDPConfigurationException(ERROR_SEALED_CONTENT_NOT_SEALED.formatted(fileName));
        }
    }

    private record DirectorySupplements(
            ObjectValue secrets,
            Map<String, Value> extensions,
            Map<String, Value> extensionSecrets,
            Set<String> criticalExtensions,
            Map<String, String> rawFiles) {}

    /**
     * Loads a PDP configuration from raw content.
     * <p>
     * The configuration ID is derived from the content in the format
     * {@code res:<sourceName>@<hash16>}, covering the pdp.json content and every
     * SAPL document.
     * </p>
     *
     * @param pdpJsonContent
     * the content of pdp.json (required, must contain algorithm)
     * @param saplDocuments
     * map of filename to SAPL document content
     * @param pdpId
     * the PDP identifier to use
     * @param sourceName
     * the source name for the derived ID (e.g., a resource directory name)
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if loading fails
     */
    public static PDPConfiguration loadFromContent(String pdpJsonContent, Map<String, String> saplDocuments,
            String pdpId, String sourceName) {
        val pdpJson   = parsePdpJson(pdpJsonContent);
        val documents = new ArrayList<>(saplDocuments.values());

        val contents = new TreeMap<String, byte[]>();
        if (pdpJsonContent != null) {
            contents.put(PDP_JSON, pdpJsonContent.getBytes(StandardCharsets.UTF_8));
        }
        putUtf8(contents, saplDocuments);
        val configurationId = ConfigurationIds.derive("res:" + sourceName, contents);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), pdpJson.compilerOptions(), documents,
                new PdpData(pdpJson.variables(), Value.EMPTY_OBJECT));
    }

    /**
     * Loads a PDP configuration from raw content for bundles.
     * <p>
     * The configuration ID is supplied by the caller from the bundle manifest and
     * is required.
     * </p>
     *
     * @param pdpJsonContent
     * the content of pdp.json (required for bundles)
     * @param secretsJsonContent
     * the content of the bundle's secrets file, or null if the bundle carries none
     * @param saplDocuments
     * map of filename to SAPL document content
     * @param pdpId
     * the PDP identifier to use
     * @param configurationId
     * the configuration ID from the bundle manifest
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if pdp.json is missing or the configuration ID is blank
     */
    public static PDPConfiguration loadFromBundle(String pdpJsonContent, String secretsJsonContent,
            Map<String, String> saplDocuments, String pdpId, String configurationId) {
        if (pdpJsonContent == null || pdpJsonContent.isBlank()) {
            throw new PDPConfigurationException(ERROR_BUNDLE_MISSING_PDP_JSON.formatted(pdpId));
        }
        if (configurationId == null || configurationId.isBlank()) {
            throw new PDPConfigurationException(ERROR_BUNDLE_CONFIGURATION_ID_REQUIRED.formatted(pdpId));
        }

        val pdpJson = parsePdpJson(pdpJsonContent);

        val secrets = secretsJsonContent != null ? parseSecretsObject(ExtensionFiles.SECRETS_FILE, secretsJsonContent)
                : Value.EMPTY_OBJECT;

        val maxDocuments = CompilationContext.intOption(pdpJson.compilerOptions(),
                CompilationContext.OPTION_MAX_POLICY_DOCUMENTS, CompilationContext.DEFAULT_MAX_POLICY_DOCUMENTS);
        enforceDocumentCount(saplDocuments.size(), maxDocuments);
        val documents = new ArrayList<>(saplDocuments.values());
        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), pdpJson.compilerOptions(), documents,
                new PdpData(pdpJson.variables(), secrets));
    }

    private static PdpJsonFile loadPdpJson(Path pdpJsonPath) {
        if (!Files.exists(pdpJsonPath)) {
            log.warn(WARN_PDP_JSON_NOT_FOUND, pdpJsonPath, CombiningAlgorithm.DEFAULT.toCanonicalString());
            return new PdpJsonFile(null,
                    new PdpJsonContent(CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        }
        try {
            val content = readCappedText(pdpJsonPath);
            return new PdpJsonFile(content, parsePdpJson(content));
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

            // The migration rejection comes first so a legacy pdp.json always surfaces
            // the migration message, even when other fields are also invalid.
            if (node.has("configurationId")) {
                throw new PDPConfigurationException(ERROR_PDP_JSON_CONFIGURATION_ID_REMOVED);
            }

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

            if (node.has(SECRETS_SECTION)) {
                throw new PDPConfigurationException(ERROR_PDP_JSON_SECRETS_NOT_ALLOWED);
            }

            val compilerOptions = parseCompilerOptions(node);

            val variables = parseValueSection(node, "variables");

            return new PdpJsonContent(algorithm, compilerOptions, variables);
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

        enforceDocumentCount(saplPaths.size(), maxFileCount);

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

    private static void enforceDocumentCount(int documentCount, int maxDocumentCount) {
        if (documentCount > maxDocumentCount) {
            throw new PDPConfigurationException(ERROR_FILE_COUNT_EXCEEDS_MAXIMUM.formatted(maxDocumentCount));
        }
    }

    private static String readSaplDocument(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_READ_SAPL_DOCUMENT.formatted(path.getFileName()), e);
        }
    }

    private static String readExtensionFileContent(Path path) {
        try {
            return readCappedText(path);
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_READ_EXTENSION_FILE.formatted(path.getFileName()), e);
        }
    }

    private static String readCappedText(Path path) throws IOException {
        enforceConfigFileSize(path, Files.size(path));
        try (val input = Files.newInputStream(path); val output = new ByteArrayOutputStream()) {
            val buffer = new byte[READ_BUFFER_SIZE];
            var total  = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                enforceConfigFileSize(path, total);
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static void enforceConfigFileSize(Path path, long size) {
        if (size > MAX_PDP_JSON_BYTES) {
            throw new PDPConfigurationException(
                    ERROR_CONFIG_FILE_SIZE_EXCEEDS_MAXIMUM.formatted(path.getFileName(), MAX_PDP_JSON_SIZE_MEBIBYTES));
        }
    }

    private record PdpJsonContent(CombiningAlgorithm algorithm, ObjectValue compilerOptions, ObjectValue variables) {}

    private record PdpJsonFile(String raw, PdpJsonContent parsed) {}

}
