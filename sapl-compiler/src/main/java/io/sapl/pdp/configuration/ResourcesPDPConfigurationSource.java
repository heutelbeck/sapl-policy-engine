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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PDP configuration source that loads configurations from classpath resources.
 * This is useful for bundling policies
 * with an application JAR.
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
 * Result: The callback is invoked once for each subdirectory configuration.
 * <p>
 * <b>Mixed (backward compatible):</b>
 *
 * <pre>
 * /policies/
 * ├── pdp.json          (default PDP)
 * ├── main.sapl         (default PDP)
 * └── tenant-a/
 *     ├── pdp.json
 *     └── tenant.sapl
 * </pre>
 *
 * Result: The callback is invoked for both "default" and "tenant-a"
 * configurations.
 * <p>
 * Since resources are static, this source invokes the callback once per
 * configuration during construction. There is no
 * hot-reloading from classpath resources.
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. All configurations are loaded during construction
 * and the callback is invoked
 * synchronously. After construction, the source holds no mutable state.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP identifier validation:</b> All PDP identifiers are validated
 * against a strict pattern (alphanumeric,
 * hyphens, underscores, dots) with a maximum length of 255 characters.</li>
 * <li><b>Classpath isolation:</b> Resources are loaded exclusively from the
 * configured classpath prefix using
 * ClassGraph, preventing access to arbitrary filesystem locations.</li>
 * <li><b>Sanitized error messages:</b> Exceptions do not expose internal
 * classpath details or system paths.</li>
 * </ul>
 *
 * @see ConfigurationUpdateCallback
 */
@Slf4j
public class ResourcesPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final String DEFAULT_RESOURCE_PATH = "/policies";

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the default resource path "/policies".
     *
     * @param callback
     * called for each configuration found in the resources
     */
    public ResourcesPDPConfigurationSource(@NonNull ConfigurationUpdateCallback callback) {
        this(DEFAULT_RESOURCE_PATH, callback);
    }

    /**
     * Creates a source loading from a custom resource path.
     *
     * @param resourcePath
     * the classpath resource path (e.g., "/policies" or "/custom/config")
     * @param callback
     * called for each configuration found in the resources
     */
    public ResourcesPDPConfigurationSource(@NonNull String resourcePath,
            @NonNull ConfigurationUpdateCallback callback) {
        this(resourcePath, "resource-config", callback);
    }

    /**
     * Creates a source loading from a custom resource path with a custom
     * configurationId.
     *
     * @param resourcePath
     * the classpath resource path
     * @param configurationId
     * the configuration identifier for all loaded configurations
     * @param callback
     * called for each configuration found in the resources
     */
    public ResourcesPDPConfigurationSource(@NonNull String resourcePath,
            @NonNull String configurationId,
            @NonNull ConfigurationUpdateCallback callback) {
        log.info("Loading PDP configurations from classpath resources: '{}'.", resourcePath);
        loadConfigurations(resourcePath, configurationId, callback);
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

    private void loadConfigurations(String resourcePath, String configurationId, ConfigurationUpdateCallback callback) {
        val    normalizedPath       = normalizeResourcePath(resourcePath);
        val    rootSaplFiles        = new HashMap<String, String>();
        val    subdirectories       = new HashSet<String>();
        val    subDirectoryData     = new HashMap<String, Map<String, String>>();
        String rootPdpJson          = null;
        var    configurationsLoaded = 0;

        try (var scanResult = new ClassGraph().acceptPaths(normalizedPath).scan()) {
            for (var resource : scanResult.getAllResources()) {
                val resourcePathStr = resource.getPath();
                val relativePath    = getRelativePath(resourcePathStr, normalizedPath);

                if (relativePath.contains("/")) {
                    val subdirName = relativePath.substring(0, relativePath.indexOf('/'));
                    subdirectories.add(subdirName);

                    val fileInSubdir = relativePath.substring(relativePath.indexOf('/') + 1);
                    subDirectoryData.computeIfAbsent(subdirName, k -> new HashMap<>());

                    if (fileInSubdir.equals(PDP_JSON)) {
                        subDirectoryData.get(subdirName).put(PDP_JSON, readResource(resource));
                    } else if (fileInSubdir.endsWith(SAPL_EXTENSION)) {
                        subDirectoryData.get(subdirName).put(fileInSubdir, readResource(resource));
                    }
                } else {
                    if (relativePath.equals(PDP_JSON)) {
                        rootPdpJson = readResource(resource);
                    } else if (relativePath.endsWith(SAPL_EXTENSION)) {
                        rootSaplFiles.put(relativePath, readResource(resource));
                    }
                }
            }
        }

        if (rootPdpJson != null || !rootSaplFiles.isEmpty()) {
            val defaultConfig = PDPConfigurationLoader.loadFromContent(rootPdpJson, rootSaplFiles, DEFAULT_PDP_ID,
                    configurationId);
            callback.onConfigurationUpdate(defaultConfig);
            configurationsLoaded++;
            log.debug("Loaded default PDP configuration with {} SAPL documents.", rootSaplFiles.size());
        }

        for (val subdirName : subdirectories) {
            if (!PDPConfigurationSource.isValidPdpId(subdirName)) {
                log.warn("Skipping subdirectory with invalid name: {}.", subdirName);
                continue;
            }

            val data      = subDirectoryData.get(subdirName);
            val pdpJson   = data.remove(PDP_JSON);
            val saplFiles = extractSaplFiles(data);

            if (pdpJson != null || !saplFiles.isEmpty()) {
                val config = PDPConfigurationLoader.loadFromContent(pdpJson, saplFiles, subdirName, configurationId);
                callback.onConfigurationUpdate(config);
                configurationsLoaded++;
                log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", subdirName, saplFiles.size());
            }
        }

        log.info("Loaded {} PDP configurations from resources.", configurationsLoaded);
    }

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
            throw new PDPConfigurationException("Failed to read resource: %s.".formatted(resource.getPath()), e);
        }
    }

}
