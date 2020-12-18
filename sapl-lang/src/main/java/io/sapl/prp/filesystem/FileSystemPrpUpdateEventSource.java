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
package io.sapl.prp.filesystem;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.prp.filemonitoring.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static io.sapl.prp.filemonitoring.FileMonitorUtil.readFile;
import static io.sapl.prp.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

@Slf4j
public class FileSystemPrpUpdateEventSource implements PrpUpdateEventSource {

    private static final String SAPL_SUFFIX = ".sapl";
    private static final String SAPL_GLOB_PATTERN = "*" + SAPL_SUFFIX;

    private final SAPLInterpreter interpreter;
    private final String watchDir;

    @SneakyThrows
    public FileSystemPrpUpdateEventSource(String policyPath, SAPLInterpreter interpreter) {
        this.interpreter = interpreter;
        watchDir = resolveHomeFolderIfPresent(policyPath);
        log.info("Monitoring for SAPL documents: {}", watchDir);
    }

    @Override
    public void dispose() {
        // NOOP
    }

    @Override
    public Flux<PrpUpdateEvent> getUpdates() {
        Map<String, SAPL> files = new HashMap<>();
        List<Update> updates = new LinkedList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(watchDir), SAPL_GLOB_PATTERN)) {
            for (var filePath : stream) {
                log.info("loading SAPL document: {}", filePath);
                var rawDocument = readFile(filePath.toFile());
                var saplDocument = interpreter.parse(rawDocument);
                files.put(filePath.toString(), saplDocument);
                updates.add(new Update(Type.PUBLISH, saplDocument, rawDocument));
            }
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }

        var seedIndex = new ImmutableFileIndex(files);
        var initialEvent = new PrpUpdateEvent(updates);

        var monitoringFlux = FileMonitorUtil.monitorDirectory(watchDir, file -> file.getName().endsWith(SAPL_SUFFIX));

        log.debug("initial event: {}", initialEvent);
        return Mono.just(initialEvent).concatWith(directoryMonitor(monitoringFlux, seedIndex));
    }

    private Flux<PrpUpdateEvent> directoryMonitor(Flux<FileEvent> fileEvents, ImmutableFileIndex seedIndex) {
        return fileEvents.scan(Tuples.of(Optional.empty(), seedIndex), this::processFileEvent)
                .filter(tuple -> tuple.getT1().isPresent()).map(Tuple2::getT1).map(Optional::get);
    }

    private Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> processFileEvent(
            Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> tuple, FileEvent fileEvent) {
        var index = tuple.getT2();
        var fileName = fileEvent.getFile().getName();
        var absoluteFileName = fileEvent.getFile().getAbsolutePath();

        log.debug("Processing file event: {} for {} - {}", fileEvent.getClass().getSimpleName(), fileName, absoluteFileName);

        if (fileEvent instanceof FileDeletedEvent) {
            log.info("unloading deleted SAPL document: {} {}", fileName, absoluteFileName);
            var update = new Update(Type.UNPUBLISH, index.get(absoluteFileName), "");
            var newIndex = index.remove(absoluteFileName);
            return Tuples.of(Optional.of(new PrpUpdateEvent(update)), newIndex);
        }
        SAPL saplDocument = null;
        String rawDocument = "";
        try {
            rawDocument = readFile(fileEvent.getFile());
            saplDocument = interpreter.parse(rawDocument);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }

        if (fileEvent instanceof FileCreatedEvent || !index.containsFile(absoluteFileName)) {
            log.info("loading new SAPL document: {}", fileName);
            var update = new Update(Type.PUBLISH, saplDocument, rawDocument);
            var newIndex = index.put(absoluteFileName, saplDocument);
            return Tuples.of(Optional.of(new PrpUpdateEvent(update)), newIndex);
        }

        // file changed

        log.info("loading updated SAPL document: {}", fileName);
        var oldDocument = index.get(absoluteFileName);
        var update1 = new Update(Type.UNPUBLISH, oldDocument, "");
        var update2 = new Update(Type.PUBLISH, saplDocument, rawDocument);
        var newIndex = index.put(absoluteFileName, saplDocument);
        return Tuples.of(Optional.of(new PrpUpdateEvent(update1, update2)), newIndex);
    }


}
