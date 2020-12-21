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

import static io.sapl.util.filemonitoring.FileMonitorUtil.readFile;
import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class FileSystemPrpUpdateEventSource implements PrpUpdateEventSource {

	private static final String SAPL_SUFFIX = ".sapl";
	private static final String SAPL_GLOB_PATTERN = "*" + SAPL_SUFFIX;

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
		Map<String, Optional<SAPL>> files = new HashMap<>();
		List<Update> updates = new LinkedList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(watchDir), SAPL_GLOB_PATTERN)) {
			for (var filePath : stream) {
				try {
					log.info("loading SAPL document: {}", filePath);
					var rawDocument = readFile(filePath.toFile());
					var saplDocument = interpreter.parse(rawDocument);
					files.put(filePath.toString(), Optional.of(saplDocument));
					updates.add(new Update(Type.PUBLISH, saplDocument, rawDocument));
				} catch (PolicyEvaluationException e) {
					files.put(filePath.toString(), Optional.empty());
				}
			}
		} catch (IOException e) {
			log.error("Unable to open directory configured to contain policies: {}", watchDir);
			return Flux.error(e);
		}

		var seedIndex = new ImmutableFileIndex(files);

		if (seedIndex.isInconsistent()) {
			updates.add(new Update(Type.INCONSISTENT, null, null));
		}

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

		log.debug("Processing file event: {} for {} - {}", fileEvent.getClass().getSimpleName(), fileName,
				absoluteFileName);

		if (fileEvent instanceof FileDeletedEvent) {
			log.info("unloading deleted SAPL document: {} {}", fileName, absoluteFileName);
			var document = index.get(absoluteFileName);
			if(document.isEmpty()) {
				return Tuples.of(Optional.empty(), index);
			}
			var update = new Update(Type.UNPUBLISH, document.get(), "");
			var newIndex = index.remove(absoluteFileName);
			return Tuples.of(Optional.of(new PrpUpdateEvent(update)), newIndex);
		}
		Optional<SAPL> saplDocument = Optional.empty();
		String rawDocument = "";
		try {
			rawDocument = readFile(fileEvent.getFile());
			saplDocument = Optional.of(interpreter.parse(rawDocument));
		} catch (PolicyEvaluationException | IOException e) {
			log.debug("Error reading file: {}. Will lead to inconsistent index.",e.getMessage());
		}

		if (fileEvent instanceof FileCreatedEvent || !index.containsFile(absoluteFileName)) {
			log.info("loading new SAPL document: {}", fileName);
			var newIndex = index.put(absoluteFileName, saplDocument);
			var updates = new LinkedList<Update>();
			if(saplDocument.isPresent()) {
				log.debug("the document has been parsed successfully. publish it to the index.");
				updates.add(new Update(Type.PUBLISH, saplDocument.get(), rawDocument));
			}
			if(newIndex.becameConsistentComparedTo(index)) {
				log.debug("the set of documents was previously INCONSISTENT and is now CONSISTENT again.");
				updates.add(new Update(Type.CONSISTENT, null, null));
			}
			if(newIndex.becameInconsistentComparedTo(index)) {
				log.debug("the set of documents was previously CONSISTENT and is now INCONSISTENT.");
				updates.add(new Update(Type.INCONSISTENT, null, null));
			}
			return Tuples.of(Optional.of(new PrpUpdateEvent(updates)), newIndex);
		}

		// file changed

		log.info("loading updated SAPL document: {}", fileName);
		var oldDocument = index.get(absoluteFileName);
		var newIndex = index.put(absoluteFileName, saplDocument);
		var updates = new LinkedList<Update>();

		if(oldDocument.isPresent()) {
			log.debug("UNPUBLISH the old document.");
			updates.add(new Update(Type.UNPUBLISH, oldDocument.get(), ""));
		}
		if(saplDocument.isPresent()) {
			log.debug("the document has been parsed successfully. publish the changed document.");
			updates.add(new Update(Type.PUBLISH, saplDocument.get(), rawDocument));
		}
		if(newIndex.becameConsistentComparedTo(index)) {
			log.debug("the set of documents was previously INCONSISTENT and is now CONSISTENT again.");
			updates.add(new Update(Type.CONSISTENT, null, null));
		}
		if(newIndex.becameInconsistentComparedTo(index)) {
			log.debug("the set of documents was previously CONSISTENT and is now INCONSISTENT.");
			updates.add(new Update(Type.INCONSISTENT, null, null));
		}

		return Tuples.of(Optional.of(new PrpUpdateEvent(updates)), newIndex);
	}

}
