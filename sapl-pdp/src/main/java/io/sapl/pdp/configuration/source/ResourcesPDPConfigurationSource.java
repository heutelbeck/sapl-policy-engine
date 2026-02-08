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
package io.sapl.pdp.configuration.source;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PDP configuration source that loads configurations from classpath resources.
 * <p>
 * The source supports both single-PDP and multi-PDP setups:
 * <p>
 * <b>Single PDP (root-level files):</b>
 *
 * <pre>
 * /policies/
 * ├── pdp.json
 * ├── access.sapl
 * └── audit.sapl
 * </pre>
 *
 * Result: pdpId = "default"
 * <p>
 * <b>Multi-PDP (subdirectories):</b>
 *
 * <pre>
 * /policies/
 * ├── production/
 * │   ├── pdp.json
 * │   └── strict.sapl
 * └── development/
 *     ├── pdp.json
 *     └── permissive.sapl
 * </pre>
 *
 * <p>
 * Since resources are static, this source loads configurations once during
 * construction. There is no hot-reloading from classpath resources.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. All configurations are loaded during construction.
 * After construction, the source holds no mutable state.
 * </p>
 */
@Slf4j
public final class ResourcesPDPConfigurationSource implements Disposable {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final String DEFAULT_RESOURCE_PATH = "/policies";

    private static final String ERROR_FAILED_TO_READ_RESOURCE = "Failed to read resource: %s.";

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the default resource path "/policies".
     *
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public ResourcesPDPConfigurationSource(@NonNull PdpVoterSource pdpVoterSource) {
        this(DEFAULT_RESOURCE_PATH, pdpVoterSource);
    }

    /**
     * Creates a source loading from a custom resource path.
     *
     * @param resourcePath
     * the classpath resource path (e.g., "/policies" or "/custom/security")
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public ResourcesPDPConfigurationSource(@NonNull String resourcePath, @NonNull PdpVoterSource pdpVoterSource) {
        log.info("Loading PDP configurations from classpath resources: '{}'.", resourcePath);
        loadConfigurations(resourcePath, pdpVoterSource);
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            log.debug("Disposed resource configuration source.");
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    private void loadConfigurations(String resourcePath, PdpVoterSource pdpVoterSource) {
        val normalizedPath = normalizeResourcePath(resourcePath);
        val scannedData    = scanResources(normalizedPath);

        var configurationsLoaded = loadRootConfiguration(normalizedPath, scannedData, pdpVoterSource);
        configurationsLoaded += loadSubdirectoryConfigurations(normalizedPath, scannedData, pdpVoterSource);

        log.info("Loaded {} PDP configurations from resources.", configurationsLoaded);
    }

    private ScannedResourceData scanResources(String normalizedPath) {
        val    rootSaplFiles    = new HashMap<String, String>();
        val    subDirectoryData = new HashMap<String, Map<String, String>>();
        String rootPdpJson      = null;

        try (var scanResult = new ClassGraph().acceptPaths(normalizedPath).scan()) {
            for (var resource : scanResult.getAllResources()) {
                val relativePath = getRelativePath(resource.getPath(), normalizedPath);

                if (relativePath.contains("/")) {
                    processSubdirectoryResource(relativePath, resource, subDirectoryData);
                } else {
                    if (PDP_JSON.equals(relativePath)) {
                        rootPdpJson = readResource(resource);
                    } else if (relativePath.endsWith(SAPL_EXTENSION)) {
                        rootSaplFiles.put(relativePath, readResource(resource));
                    }
                }
            }
        }

        return new ScannedResourceData(rootPdpJson, rootSaplFiles, subDirectoryData);
    }

    private void processSubdirectoryResource(String relativePath, Resource resource,
            Map<String, Map<String, String>> subDirectoryData) {
        val slashIndex   = relativePath.indexOf('/');
        val subdirName   = relativePath.substring(0, slashIndex);
        val fileInSubdir = relativePath.substring(slashIndex + 1);

        subDirectoryData.computeIfAbsent(subdirName, k -> new HashMap<>());

        if (PDP_JSON.equals(fileInSubdir) || fileInSubdir.endsWith(SAPL_EXTENSION)) {
            subDirectoryData.get(subdirName).put(fileInSubdir, readResource(resource));
        }
    }

    private int loadRootConfiguration(String normalizedPath, ScannedResourceData data, PdpVoterSource pdpVoterSource) {
        if (data.rootPdpJson() == null && data.rootSaplFiles().isEmpty()) {
            return 0;
        }

        val sourcePath    = "/" + normalizedPath;
        val defaultConfig = PDPConfigurationLoader.loadFromContent(data.rootPdpJson(), data.rootSaplFiles(),
                PdpIdValidator.DEFAULT_PDP_ID, sourcePath);
        pdpVoterSource.loadConfiguration(defaultConfig, true);
        log.debug("Loaded default PDP configuration with {} SAPL documents.", data.rootSaplFiles().size());
        return 1;
    }

    private int loadSubdirectoryConfigurations(String normalizedPath, ScannedResourceData data,
            PdpVoterSource pdpVoterSource) {
        var count = 0;

        for (val entry : data.subDirectoryData().entrySet()) {
            val subdirName = entry.getKey();

            if (!PdpIdValidator.isValidPdpId(subdirName)) {
                log.warn("Skipping subdirectory with invalid name: {}.", subdirName);
                continue;
            }

            val subdirData = entry.getValue();
            val pdpJson    = subdirData.remove(PDP_JSON);
            val saplFiles  = extractSaplFiles(subdirData);

            if (pdpJson != null || !saplFiles.isEmpty()) {
                val sourcePath = "/" + normalizedPath + "/" + subdirName;
                val config     = PDPConfigurationLoader.loadFromContent(pdpJson, saplFiles, subdirName, sourcePath);
                pdpVoterSource.loadConfiguration(config, true);
                count++;
                log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", subdirName, saplFiles.size());
            }
        }

        return count;
    }

    private record ScannedResourceData(
            String rootPdpJson,
            Map<String, String> rootSaplFiles,
            Map<String, Map<String, String>> subDirectoryData) {}

    private Map<String, String> extractSaplFiles(Map<String, String> content) {
        val saplFiles = new HashMap<String, String>();
        for (val entry : content.entrySet()) {
            if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                saplFiles.put(entry.getKey(), entry.getValue());
            }
        }
        return saplFiles;
    }

    private String normalizeResourcePath(String path) {
        var normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getRelativePath(String fullPath, String basePath) {
        if (fullPath.startsWith(basePath + "/")) {
            return fullPath.substring(basePath.length() + 1);
        }
        if (fullPath.startsWith(basePath)) {
            return fullPath.substring(basePath.length());
        }
        return fullPath;
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.load(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_READ_RESOURCE.formatted(resource.getPath()), e);
        }
    }

}
