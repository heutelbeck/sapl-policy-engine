/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.resources;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.util.JarPathUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.IOUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
public class ResourcesPrpUpdateEventSource implements PrpUpdateEventSource {

    private static final String POLICY_FILE_SUFFIX = ".sapl";
    private static final String POLICY_FILE_GLOB_PATTERN = "*" + POLICY_FILE_SUFFIX;

    private final SAPLInterpreter interpreter;
    private final PrpUpdateEvent initializingPrpUpdate;

    public ResourcesPrpUpdateEventSource(@NonNull String policyPath, @NonNull SAPLInterpreter interpreter) {
        this(ResourcesPrpUpdateEventSource.class, policyPath, interpreter);
    }

    public ResourcesPrpUpdateEventSource(@NonNull Class<?> clazz, @NonNull String policyPath,
                                         @NonNull SAPLInterpreter interpreter) {
        this.interpreter = interpreter;
        log.info("Loading a static set of policies from the bundled ressources");
        URL policyFolderUrl = clazz.getResource(policyPath);
        if (policyFolderUrl == null) {
            throw new RuntimeException("Policy folder in application resources is either empty or not present at all. Path:" + policyPath);
        }

        if ("jar".equals(policyFolderUrl.getProtocol())) {
            initializingPrpUpdate = readPoliciesFromJar(policyFolderUrl);
        } else {
            initializingPrpUpdate = readPoliciesFromDirectory(policyFolderUrl);
        }
    }

    final PrpUpdateEvent readPoliciesFromJar(URL policiesFolderUrl) {
        log.debug("reading policies from jar {}", policiesFolderUrl);
        val jarPathElements = policiesFolderUrl.toString().split("!");
        val jarFilePath = JarPathUtil.getJarFilePath(jarPathElements);
        val policiesDirPath = new StringBuilder();
        for (int i = 1; i < jarPathElements.length; i++) {
            policiesDirPath.append(jarPathElements[i]);
        }
        if (policiesDirPath.charAt(0) == '/') {
            policiesDirPath.deleteCharAt(0);
        }
        List<Update> updates = new LinkedList<>();
        final String policiesDirPathStr = policiesDirPath.toString();
        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            Enumeration<? extends ZipEntry> e = zipFile.entries();

            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(policiesDirPathStr)
                        && entry.getName().endsWith(POLICY_FILE_SUFFIX)) {
                    log.info("load SAPL document: {}", entry.getName());
                    BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                    var rawDocument = IOUtils.toString(bis, StandardCharsets.UTF_8);
                    bis.close();
                    final SAPL saplDocument = interpreter.parse(rawDocument);
                    updates.add(new Update(Type.PUBLISH, saplDocument, rawDocument));
                }
            }
        } catch (IOException | PolicyEvaluationException e) {
            throw Exceptions.propagate(e);
        }
        return new PrpUpdateEvent(updates);
    }

    final PrpUpdateEvent readPoliciesFromDirectory(URL policiesFolderUrl) {
        log.debug("reading policies from directory {}", policiesFolderUrl);
        List<Update> updates = new LinkedList<>();
        Path policiesDirectoryPath;
        try {
            policiesDirectoryPath = Paths.get(policiesFolderUrl.toURI());
        } catch (URISyntaxException e) {
            throw Exceptions.propagate(e);

        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policiesDirectoryPath, POLICY_FILE_GLOB_PATTERN)) {
            for (Path filePath : stream) {
                log.debug("loading SAPL document: {}", filePath);
                var rawDocument = readFile(filePath);
                var saplDocument = interpreter.parse(rawDocument);
                updates.add(new Update(Type.PUBLISH, saplDocument, rawDocument));
            }
        } catch (IOException | PolicyEvaluationException e) {
            throw Exceptions.propagate(e);
        }
        return new PrpUpdateEvent(updates);
    }

    @Override
    public void dispose() {
        // NOP nothing to dispose of
    }

    @Override
    public Flux<PrpUpdateEvent> getUpdates() {
        return Flux.just(initializingPrpUpdate);
    }

    public static String readFile(Path filePath) throws IOException {
        var fis = Files.newInputStream(filePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        }
    }

}
