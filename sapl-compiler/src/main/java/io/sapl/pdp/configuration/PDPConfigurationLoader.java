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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility for loading PDP configurations from filesystem paths.
 */
@Slf4j
@UtilityClass
public class PDPConfigurationLoader {

    private static final String       PDP_JSON       = "pdp.json";
    private static final String       SAPL_EXTENSION = ".sapl";
    private static final ObjectMapper MAPPER         = createMapper();

    private static ObjectMapper createMapper() {
        val mapper = new ObjectMapper();
        mapper.registerModule(new SaplJacksonModule());
        return mapper;
    }

    /**
     * Loads a PDP configuration from a directory path.
     *
     * @param path
     * the directory containing pdp.json and *.sapl files
     * @param pdpId
     * the PDP identifier to use
     * @param configurationId
     * the configuration identifier (metadata)
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if loading fails
     */
    public static PDPConfiguration loadFromDirectory(Path path, String pdpId, String configurationId) {
        val pdpJsonPath = path.resolve(PDP_JSON);
        val pdpJson     = loadPdpJson(pdpJsonPath);
        val documents   = loadSaplDocuments(path);

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), documents, pdpJson.variables());
    }

    /**
     * Loads a PDP configuration from raw content (for bundle extraction).
     *
     * @param pdpJsonContent
     * the content of pdp.json, or null if not present
     * @param saplDocuments
     * map of filename to SAPL document content
     * @param pdpId
     * the PDP identifier to use
     * @param configurationId
     * the configuration identifier (metadata)
     *
     * @return the loaded configuration
     *
     * @throws PDPConfigurationException
     * if loading fails
     */
    public static PDPConfiguration loadFromContent(String pdpJsonContent, Map<String, String> saplDocuments,
            String pdpId, String configurationId) {
        val pdpJson   = parsePdpJson(pdpJsonContent);
        val documents = new ArrayList<>(saplDocuments.values());

        return new PDPConfiguration(pdpId, configurationId, pdpJson.algorithm(), documents, pdpJson.variables());
    }

    private static PdpJsonContent loadPdpJson(Path pdpJsonPath) {
        if (!Files.exists(pdpJsonPath)) {
            return PdpJsonContent.defaults();
        }
        try {
            val content = Files.readString(pdpJsonPath, StandardCharsets.UTF_8);
            return parsePdpJson(content);
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to read pdp.json from %s.".formatted(pdpJsonPath), e);
        }
    }

    private static PdpJsonContent parsePdpJson(String content) {
        if (content == null || content.isBlank()) {
            if (content != null) {
                log.warn("Empty or whitespace-only pdp.json content, using defaults.");
            }
            return PdpJsonContent.defaults();
        }
        try {
            val node = MAPPER.readTree(content);

            CombiningAlgorithm algorithm = CombiningAlgorithm.DENY_OVERRIDES;
            if (node.has("algorithm")) {
                algorithm = MAPPER.treeToValue(node.get("algorithm"), CombiningAlgorithm.class);
            }

            Map<String, Value> variables = new HashMap<>();
            if (node.has("variables")) {
                val variablesNode = node.get("variables");
                for (val property : variablesNode.properties()) {
                    val value = MAPPER.treeToValue(property.getValue(), Value.class);
                    variables.put(property.getKey(), value);
                }
            }

            return new PdpJsonContent(algorithm, variables);
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to parse pdp.json content.", e);
        }
    }

    private static List<String> loadSaplDocuments(Path directory) {
        List<Path> saplPaths;
        try (Stream<Path> paths = Files.list(directory)) {
            saplPaths = paths.filter(p -> p.toString().endsWith(SAPL_EXTENSION)).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to list SAPL files in directory.", e);
        }

        val documents = new ArrayList<String>(saplPaths.size());
        for (val path : saplPaths) {
            documents.add(readSaplDocument(path));
        }
        return documents;
    }

    private static String readSaplDocument(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to read SAPL document.", e);
        }
    }

    private record PdpJsonContent(CombiningAlgorithm algorithm, Map<String, Value> variables) {
        static PdpJsonContent defaults() {
            return new PdpJsonContent(CombiningAlgorithm.DENY_OVERRIDES, Map.of());
        }
    }

}
