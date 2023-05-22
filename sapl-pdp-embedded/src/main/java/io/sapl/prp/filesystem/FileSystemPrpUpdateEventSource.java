/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

	private final String watchDir;

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
		var seedIndex = new ImmutableFileIndex(this.watchDir, interpreter);
		var initialEvent = seedIndex.getUpdateEvent();
		var monitoringFlux = FileMonitorUtil.monitorDirectory(watchDir, file -> file.getName().endsWith(SAPL_SUFFIX));
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
		var index = tuple.getT2();
		var newIndex = index.afterFileEvent(fileEvent);
		log.debug("Update event: {}", newIndex.getUpdateEvent());
		return Tuples.of(Optional.of(newIndex.getUpdateEvent()), newIndex);
	}

}
