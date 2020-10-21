/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp.embedded.config.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.directorywatcher.InitialWatchEvent;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@Slf4j
public class ResourcesPDPConfigurationProvider implements PDPConfigurationProvider {

    private static final String DEFAULT_CONFIG_PATH = "/policies";

    private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

    private static final Pattern CONFIG_FILE_REGEX_PATTERN = Pattern.compile("pdp\\.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PolicyDecisionPointConfiguration config;

    private final ReentrantLock lock = new ReentrantLock();

    private String path;

    private Scheduler dirWatcherScheduler;

    private ReplayProcessor<WatchEvent<Path>> dirWatcherEventProcessor = ReplayProcessor
            .cacheLastOrDefault(InitialWatchEvent.INSTANCE);

    public ResourcesPDPConfigurationProvider() throws PDPConfigurationException, IOException, URISyntaxException {
        this(DEFAULT_CONFIG_PATH);
    }

    public ResourcesPDPConfigurationProvider(@NonNull String configPath)
            throws PDPConfigurationException, IOException, URISyntaxException {
        this(ResourcesPDPConfigurationProvider.class, configPath);
    }

    public ResourcesPDPConfigurationProvider(@NonNull Class<?> clazz, @NonNull String configPath)
            throws PDPConfigurationException, IOException, URISyntaxException {
        URL configFolderUrl = clazz.getResource(configPath);
        if (configFolderUrl == null) {
            throw new PDPConfigurationException("Config folder not found. Path:" + configPath + " - URL: null");
        }

        if ("jar".equals(configFolderUrl.getProtocol())) {
            readConfigFromJar(configFolderUrl);
        } else {
            readConfigFromDirectory(configFolderUrl);
        }

        this.path = Paths.get(configFolderUrl.toURI()).toString();

        final Path watchDir = Paths.get(path);
        final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);

        final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
                CONFIG_FILE_REGEX_PATTERN);
        dirWatcherScheduler = Schedulers.newElastic("configWatcher");
        final Flux<WatchEvent<Path>> dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
            adapter.setSink(sink);
            directoryWatcher.watch(adapter);
        }).doOnNext(event -> {
            updateConfig(event);
            dirWatcherEventProcessor.onNext(event);
        }).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

        dirWatcherFlux.subscribe();

        if (this.config == null) {
            log.debug("config is null - using default config");
            this.config = new PolicyDecisionPointConfiguration();
        }
    }

    private void updateConfig(WatchEvent<Path> watchEvent) {
        final WatchEvent.Kind<Path> kind = watchEvent.kind();
        final Path fileName = watchEvent.context();
        try {
            lock.lock();

            final Path absoluteFilePath = Paths.get(path, fileName.toString());
            if (kind == ENTRY_CREATE) {
                log.info("reading pdp config from {}", fileName);
                config = MAPPER.readValue(absoluteFilePath.toFile(), PolicyDecisionPointConfiguration.class);
            } else if (kind == ENTRY_DELETE) {
                log.info("deleted pdp config file {}. Using default configuration", fileName);
                config = new PolicyDecisionPointConfiguration();
            } else if (kind == ENTRY_MODIFY) {
                log.info("updating pdp config from {}", fileName);
                config = MAPPER.readValue(absoluteFilePath.toFile(), PolicyDecisionPointConfiguration.class);
            } else {
                log.error("unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
            }
        } catch (IOException e) {
            log.error("Error while updating the pdp config.", e);
        } finally {
            lock.unlock();
        }
    }

    private void readConfigFromJar(URL configFolderUrl) {
        log.debug("reading config from jar {}", configFolderUrl);
        final String[] jarPathElements = configFolderUrl.toString().split("!");
        final String jarFilePath = jarPathElements[0].substring("jar:file:".length());
        final StringBuilder dirPath = new StringBuilder();
        for (int i = 1; i < jarPathElements.length; i++) {
            dirPath.append(jarPathElements[i]);
        }
        if (dirPath.charAt(0) == File.separatorChar) {
            dirPath.deleteCharAt(0);
        }
        final String configFilePath = dirPath.append(File.separatorChar).append(CONFIG_FILE_GLOB_PATTERN).toString();

        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            Enumeration<? extends ZipEntry> e = zipFile.entries();

            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().equals(configFilePath)) {
                    log.debug("load: {}", entry.getName());
                    BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                    String fileContentsStr = IOUtils.toString(bis, StandardCharsets.UTF_8);
                    bis.close();
                    this.config = MAPPER.readValue(fileContentsStr, PolicyDecisionPointConfiguration.class);
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error while reading config from jar", e);
        }
    }

    private void readConfigFromDirectory(URL configFolderUrl) throws IOException, URISyntaxException {
        log.debug("reading config from directory {}", configFolderUrl);
        Path configDirectoryPath = Paths.get(configFolderUrl.toURI());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectoryPath, CONFIG_FILE_GLOB_PATTERN)) {
            for (Path filePath : stream) {
                log.debug("load: {}", filePath);
                this.config = MAPPER.readValue(filePath.toFile(), PolicyDecisionPointConfiguration.class);
            }
        }
    }

    public ResourcesPDPConfigurationProvider(PolicyDecisionPointConfiguration config) {
        this.config = config;
    }


    @Override
    public Flux<DocumentsCombinator> getDocumentsCombinator() {
        // @formatter:off
        return dirWatcherEventProcessor
                .map(event -> config.getAlgorithm())
                .distinctUntilChanged()
                .map(algorithm -> {
                    log.trace("|-- Current PDP config: combining algorithm = {}", algorithm);
                    return convert(algorithm);
                });
        // @formatter:on
    }


    @Override
    public Flux<Map<String, JsonNode>> getVariables() {
        // @formatter:off
        return dirWatcherEventProcessor
                .map(event -> config.getVariables())
                .distinctUntilChanged()
                .doOnNext(variables -> log.trace("|-- Current PDP config: variables = {}", variables));
        // @formatter:on
    }

}
