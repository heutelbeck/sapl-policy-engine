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
package io.sapl.prp.resources;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.directorywatcher.InitialWatchEvent;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
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
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private static final String DEFAULT_POLICIES_PATH = "/policies";

    private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";

    private static final Pattern POLICY_FILE_REGEX_PATTERN = Pattern.compile(".+\\.sapl");

    private static final String POLICY_FILE_SUFFIX = ".sapl";

    private final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

    private final ParsedDocumentIndex parsedDocIdx;

    private String path;

    private Scheduler dirWatcherScheduler;

    private final ReentrantLock lock = new ReentrantLock();

    private ReplayProcessor<WatchEvent<Path>> dirWatcherEventProcessor = ReplayProcessor
            .cacheLastOrDefault(InitialWatchEvent.INSTANCE);

    public ResourcesPolicyRetrievalPoint() throws IOException, URISyntaxException, PolicyEvaluationException {
        this(DEFAULT_POLICIES_PATH, new SimpleParsedDocumentIndex());
    }

    public ResourcesPolicyRetrievalPoint(@NonNull String policyPath, @NonNull ParsedDocumentIndex parsedDocumentIndex)
            throws IOException, URISyntaxException, PolicyEvaluationException {
        this(ResourcesPolicyRetrievalPoint.class, policyPath, parsedDocumentIndex);
    }

    public ResourcesPolicyRetrievalPoint(@NonNull Class<?> clazz, @NonNull String policyPath,
                                         @NonNull ParsedDocumentIndex parsedDocumentIndex)
            throws IOException, URISyntaxException, PolicyEvaluationException {

        URL policyFolderUrl = clazz.getResource(policyPath);
        if (policyFolderUrl == null) {
            throw new PolicyEvaluationException("Policy folder not found. Path:" + policyPath);
        }

        this.parsedDocIdx = parsedDocumentIndex;

        if ("jar".equals(policyFolderUrl.getProtocol())) {
            readPoliciesFromJar(policyFolderUrl);
        } else {
            readPoliciesFromDirectory(policyFolderUrl);
        }
        this.parsedDocIdx.setLiveMode();

        this.path = Paths.get(policyFolderUrl.toURI()).toString();
        LOGGER.info("setting up directory watcher for path: {}", this.path);
        final Path watchDir = Paths.get(path);
        final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);

        final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
                POLICY_FILE_REGEX_PATTERN);
        dirWatcherScheduler = Schedulers.newElastic("policyWatcher");
        final Flux<WatchEvent<Path>> dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
            adapter.setSink(sink);
            directoryWatcher.watch(adapter);
        }).doOnNext(event -> {
            updateIndex(event);
            dirWatcherEventProcessor.onNext(event);
        }).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler);

        dirWatcherFlux.subscribe();
    }

    private void updateIndex(WatchEvent<Path> watchEvent) {
        final WatchEvent.Kind<Path> kind = watchEvent.kind();
        final Path fileName = watchEvent.context();
        try {
            lock.lock();

            final Path absoluteFilePath = Paths.get(path, fileName.toString());
            final String absoluteFileName = absoluteFilePath.toString();
            if (kind == ENTRY_CREATE) {
                LOGGER.info("adding {} to index", fileName);
                final SAPL saplDocument = interpreter.parse(Files.newInputStream(absoluteFilePath));
                parsedDocIdx.put(absoluteFileName, saplDocument);
            } else if (kind == ENTRY_DELETE) {
                LOGGER.info("removing {} from index", fileName);
                parsedDocIdx.remove(absoluteFileName);
            } else if (kind == ENTRY_MODIFY) {
                LOGGER.info("updating {} in index", fileName);
                final SAPL saplDocument = interpreter.parse(Files.newInputStream(absoluteFilePath));
                parsedDocIdx.put(absoluteFileName, saplDocument);
            } else {
                LOGGER.error("unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
            }
        } catch (IOException | PolicyEvaluationException e) {
            LOGGER.error("Error while updating the document index.", e);
        } finally {
            lock.unlock();
        }
    }

    private void readPoliciesFromJar(URL policiesFolderUrl) throws PolicyEvaluationException {
        LOGGER.debug("reading policies from jar {}", policiesFolderUrl);
        final String[] jarPathElements = policiesFolderUrl.toString().split("!");
        final String jarFilePath = jarPathElements[0].substring("jar:file:".length());
        final StringBuilder policiesDirPath = new StringBuilder();
        for (int i = 1; i < jarPathElements.length; i++) {
            policiesDirPath.append(jarPathElements[i]);
        }
        if (policiesDirPath.charAt(0) == File.separatorChar) {
            policiesDirPath.deleteCharAt(0);
        }
        final String policiesDirPathStr = policiesDirPath.toString();


        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            Enumeration<? extends ZipEntry> e = zipFile.entries();

            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(policiesDirPathStr)
                        && entry.getName().endsWith(POLICY_FILE_SUFFIX)) {
                    LOGGER.debug("load: {}", entry.getName());
                    BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                    String fileContentsStr = IOUtils.toString(bis, StandardCharsets.UTF_8);
                    bis.close();
                    final SAPL saplDocument = interpreter.parse(fileContentsStr);
                    this.parsedDocIdx.put(entry.getName(), saplDocument);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while reading config from jar", e);
        }
    }

    private void readPoliciesFromDirectory(URL policiesFolderUrl)
            throws IOException, URISyntaxException, PolicyEvaluationException {
        LOGGER.debug("reading policies from directory {}", policiesFolderUrl);
        Path policiesDirectoryPath = Paths.get(policiesFolderUrl.toURI());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policiesDirectoryPath, POLICY_FILE_GLOB_PATTERN)) {
            for (Path filePath : stream) {
                LOGGER.debug("load: {}", filePath);
                final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
                this.parsedDocIdx.put(filePath.toString(), saplDocument);
            }
        }
    }

    @Override
    public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
                                                        FunctionContext functionCtx, Map<String, JsonNode> variables) {
        return dirWatcherEventProcessor.flatMap(event -> {
            try {
                lock.lock();
                return Flux.from(parsedDocIdx.retrievePolicies(authzSubscription, functionCtx, variables))
                        .doOnNext(this::logMatching);
            } finally {
                lock.unlock();
            }
        });
    }

    private void logMatching(PolicyRetrievalResult result) {
        if (result.getMatchingDocuments().isEmpty()) {
            LOGGER.trace("|-- Matching documents: NONE");
        } else {
            LOGGER.trace("|-- Matching documents:");
            for (SAPL doc : result.getMatchingDocuments()) {
                LOGGER.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(),
                        doc.getPolicyElement().getClass().getName());
            }
        }
        LOGGER.trace("|");
    }

}
