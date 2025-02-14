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
package io.sapl.util.filemonitoring;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class FileMonitorUtil {

    private static final long   POLL_INTERVAL_IN_MS = 500;
    private static final String SAPL_FILE_EXTENSION = "sapl";

    public static Path resolveHomeFolderIfPresent(String policyPath) {
        policyPath = policyPath.replace("/", File.separator);

        if (policyPath.startsWith("~" + File.separator))
            return Paths.get(getUserHomeProperty() + policyPath.substring(1));

        return Paths.get(policyPath);
    }

    static String getUserHomeProperty() {
        return System.getProperty("user.home");
    }

    public static Flux<FileEvent> monitorDirectory(final Path watchDir, final FileFilter fileFilter) {
        return Flux.push(emitter -> {
            final var              adaptor = new FileEventAdaptor(emitter);
            final var              monitor = new FileAlterationMonitor(POLL_INTERVAL_IN_MS);
            FileAlterationObserver observer;
            try {
                observer = FileAlterationObserver.builder().setFile(watchDir.toFile()).setFileFilter(fileFilter).get();
                monitor.addObserver(observer);
                observer.addListener(adaptor);
                emitter.onDispose(() -> {
                    try {
                        monitor.stop();
                    } catch (Exception e) {
                        emitter.error(e);
                    }
                });

                try {
                    monitor.start();
                } catch (Exception e) {
                    emitter.error(e);
                }
            } catch (IOException e) {
                throw new PolicyEvaluationException("failed to monitor %s: %s", watchDir.toString(), e.getMessage());
            }
        });
    }

    public static List<Path> findSaplDocuments(String rawPath) throws IOException {
        final var path = Paths.get(rawPath);
        return findSaplDocuments(path);
    }

    public static List<Path> findSaplDocuments(Path path) throws IOException {

        if (!Files.isDirectory(path)) {
            throw new IOException("Provided path for policies not a path: " + path);
        }

        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(p -> !Files.isDirectory(p) && p.toString().toLowerCase().endsWith(SAPL_FILE_EXTENSION))
                    .toList();
        }
    }
}
