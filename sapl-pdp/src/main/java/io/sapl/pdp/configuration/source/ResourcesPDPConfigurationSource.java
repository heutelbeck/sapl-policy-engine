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
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PDP configuration source that loads configurations from classpath resources.
 * <p>
 * The source supports both single-PDP and multi-PDP setups:
 * <p>
 * <b>Single PDP (root-level files):</b>
 *
 * <pre>
 * /policies/
 *   pdp.json
 *   access.sapl
 *   audit.sapl
 * </pre>
 *
 * Result: pdpId = "default"
 * <p>
 * <b>Multi-PDP (subdirectories):</b>
 *
 * <pre>
 * /policies/
 *   production/
 *     pdp.json
 *     strict.sapl
 *   development/
 *     pdp.json
 *     permissive.sapl
 * </pre>
 *
 * <p>
 * Since resources are static, this source loads configurations once on first
 * subscribe and emits a {@link ConfigurationEvent.NewConfiguration} per discovered PDP.
 * There is no hot-reloading from classpath resources.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe.
 * </p>
 */
@Slf4j
public final class ResourcesPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final String DEFAULT_RESOURCE_PATH = "/policies";

    private static final String ERROR_FAILED_TO_READ_RESOURCE = "Failed to read resource: %s.";
    private static final String ERROR_NO_CONFIGURATIONS_FOUND = "No PDP configurations found at resource path '%s'. Ensure SAPL documents exist at this classpath location.";

    private final String                            resourcePath;
    private final Set<Consumer<ConfigurationEvent>> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated   = new AtomicBoolean(false);
    private final AtomicBoolean                     closed      = new AtomicBoolean(false);

    /**
     * Creates a source loading from the default resource path "/policies".
     */
    public ResourcesPDPConfigurationSource() {
        this(DEFAULT_RESOURCE_PATH);
    }

    /**
     * Creates a source loading from a custom resource path.
     *
     * @param resourcePath the classpath resource path (e.g., "/policies" or
     * "/custom/security")
     */
    public ResourcesPDPConfigurationSource(@NonNull String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        if (closed.get()) {
            return;
        }
        subscribers.add(listener);
        if (activated.compareAndSet(false, true)) {
            loadAndEmit();
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        subscribers.remove(listener);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subscribers.clear();
            log.debug("Closed resource configuration source.");
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private void loadAndEmit() {
        log.info("Loading PDP configurations from classpath resources: '{}'.", resourcePath);
        val normalizedPath = normalizeResourcePath(resourcePath);
        val scannedData    = scanResources(normalizedPath);

        var loaded = emitRootConfiguration(normalizedPath, scannedData);
        loaded += emitSubdirectoryConfigurations(normalizedPath, scannedData);

        log.info("Loaded {} PDP configurations from resources.", loaded);
        if (loaded == 0) {
            throw new PDPConfigurationException(ERROR_NO_CONFIGURATIONS_FOUND.formatted(resourcePath));
        }
    }

    private void emit(PDPConfiguration configuration) {
        // Resources are static, so a compile error propagates as an exception
        // through the subscriber call rather than being retained for reload.
        val event = new ConfigurationEvent.NewConfiguration(configuration);
        for (val subscriber : subscribers) {
            subscriber.accept(event);
        }
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

    private int emitRootConfiguration(String normalizedPath, ScannedResourceData data) {
        if (data.rootPdpJson() == null && data.rootSaplFiles().isEmpty()) {
            return 0;
        }

        val sourcePath    = "/" + normalizedPath;
        val defaultConfig = PDPConfigurationLoader.loadFromContent(data.rootPdpJson(), data.rootSaplFiles(),
                StreamingPolicyDecisionPoint.DEFAULT_PDP_ID, sourcePath);
        emit(defaultConfig);
        log.debug("Loaded default PDP configuration with {} SAPL documents.", data.rootSaplFiles().size());
        return 1;
    }

    private int emitSubdirectoryConfigurations(String normalizedPath, ScannedResourceData data) {
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
                emit(config);
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
