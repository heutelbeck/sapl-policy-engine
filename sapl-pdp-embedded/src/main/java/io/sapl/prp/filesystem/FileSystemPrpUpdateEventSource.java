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
package io.sapl.prp.filesystem;

import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.nio.file.Path;
import java.util.Optional;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.util.filemonitoring.FileEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class FileSystemPrpUpdateEventSource implements PrpUpdateEventSource {

    private static final String SAPL_SUFFIX = ".sapl";

    private final SAPLInterpreter interpreter;

    private final Path watchDir;

    public FileSystemPrpUpdateEventSource(String policyPath, SAPLInterpreter interpreter) {
        this.interpreter = interpreter;
        watchDir         = resolveHomeFolderIfPresent(policyPath);
        log.info("Monitoring for SAPL documents: {}", watchDir);
    }

    @Override
    public void dispose() {
        // NOOP
    }

    @Override
    public Flux<PrpUpdateEvent> getUpdates() {
        final var seedIndex    = new ImmutableFileIndex(this.watchDir, interpreter);
        final var initialEvent = seedIndex.getUpdateEvent();
        // If the predicate filters inside the monitorDirectory by suffix, then no
        // sub-folders are monitored.
        // I do not know why. But putting a filter after the monitorDirectory solves the
        // issue.
        final var monitoringFlux = FileMonitorUtil.monitorDirectory(watchDir, file -> true)
                .filter(event -> event.file() != null)
                .filter(event -> event.file().toAbsolutePath().toString().endsWith(SAPL_SUFFIX));
        log.debug("Initial event: {}", initialEvent);
        return Mono.just(initialEvent).concatWith(directoryMonitor(monitoringFlux, seedIndex));
    }

    private Flux<PrpUpdateEvent> directoryMonitor(Flux<FileEvent> fileEvents, ImmutableFileIndex seedIndex) {
        return fileEvents.scan(createInitialTuple(seedIndex), this::processFileEvent)
                .filter(tuple -> tuple.getT1().isPresent()).map(Tuple2::getT1).map(Optional::get);
    }

    private Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> createInitialTuple(ImmutableFileIndex seedIndex) {
        return Tuples.of(Optional.empty(), seedIndex);
    }

    private Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> processFileEvent(
            Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> tuple, FileEvent fileEvent) {
        final var index    = tuple.getT2();
        final var newIndex = index.afterFileEvent(fileEvent);
        log.debug("Update event: {}", newIndex.getUpdateEvent());
        return Tuples.of(Optional.of(newIndex.getUpdateEvent()), newIndex);
    }

}
