package io.sapl.reimpl.prp.filesystem;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.directorywatcher.DirectoryWatchEventFluxSinkAdapter;
import io.sapl.directorywatcher.DirectoryWatcher;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEvent.Type;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import io.sapl.reimpl.prp.PrpUpdateEventSource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@Slf4j
public class FileSystemPrpUpdateEventSource implements PrpUpdateEventSource {

	private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";
	private static final Pattern POLICY_FILE_REGEX_PATTERN = Pattern.compile(".+\\.sapl");

	private final SAPLInterpreter interpreter;
	private final Path watchDir;
	private Scheduler dirWatcherScheduler;
	private Flux<WatchEvent<Path>> dirWatcherFlux;

	public FileSystemPrpUpdateEventSource(String policyPath, SAPLInterpreter interpreter) {
		this.interpreter = interpreter;

		watchDir = fileSystemPath(policyPath);
		// Set up directory watcher

		final DirectoryWatcher directoryWatcher = new DirectoryWatcher(watchDir);
		final DirectoryWatchEventFluxSinkAdapter adapter = new DirectoryWatchEventFluxSinkAdapter(
				POLICY_FILE_REGEX_PATTERN);
		dirWatcherScheduler = Schedulers.newElastic("policyWatcher");
		dirWatcherFlux = Flux.<WatchEvent<Path>>push(sink -> {
			adapter.setSink(sink);
			directoryWatcher.watch(adapter);
		}).doOnCancel(adapter::cancel).subscribeOn(dirWatcherScheduler).share();
	}

	private final Path fileSystemPath(String policyPath) {
		String path = "";
		// First resolve actual path
		if (policyPath.startsWith("~" + File.separator) || policyPath.startsWith("~/")) {
			path = System.getProperty("user.home") + policyPath.substring(1);
		} else if (policyPath.startsWith("~")) {
			throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames");
		} else {
			path = policyPath;
		}
		return Paths.get(path);
	}

	@Override
	public void dispose() {
		dirWatcherScheduler.dispose();
	}

	@Override
	public Flux<PrpUpdateEvent> getUpdates() {
		Map<String, SAPL> files = new HashMap<>();
		List<Update> updates = new LinkedList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(watchDir, POLICY_FILE_GLOB_PATTERN)) {
			for (var filePath : stream) {
				log.info("loading SAPL document: {}", filePath);
				var rawDocument = readFile(filePath);
				var saplDocument = interpreter.parse(rawDocument);
				files.put(filePath.toString(), saplDocument);
				updates.add(new Update(Type.PUBLISH, saplDocument, rawDocument));
			}
		} catch (IOException | PolicyEvaluationException e) {
			throw new RuntimeException("FATAL ERROR: building initial PrpUpdateEvent: " + e.getMessage(), e);
		}

		var seedIndex = new ImmutableFileIndex(files);
		var initialEvent = new PrpUpdateEvent(updates);
		log.debug("initial event: {}", initialEvent);
		return Mono.just(initialEvent).concatWith(directoryMonitor(seedIndex));
	}

	private Flux<PrpUpdateEvent> directoryMonitor(ImmutableFileIndex seedIndex) {
		return Flux.from(dirWatcherFlux).scan(Tuples.of(Optional.empty(), seedIndex), this::processWatcherEvent)
				.filter(tuple -> tuple.getT1().isPresent()).map(Tuple2::getT1).map(Optional::get)
				.distinctUntilChanged();
	}

	private Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> processWatcherEvent(
			Tuple2<Optional<PrpUpdateEvent>, ImmutableFileIndex> tuple, WatchEvent<Path> watchEvent) {
		var index = tuple.getT2();
		var kind = watchEvent.kind();
		var fileName = watchEvent.context();
		var absoluteFilePath = Path.of(watchDir.toAbsolutePath().toString(), fileName.toString());
		var absoluteFileName = absoluteFilePath.toString();

		if (kind != ENTRY_DELETE && kind != ENTRY_CREATE && kind != ENTRY_MODIFY) {
			log.debug("dropping unknown kind of directory watch event: {}", kind != null ? kind.name() : "null");
			return Tuples.of(Optional.empty(), index);
		}

		if (kind == ENTRY_DELETE) {
			log.info("unloading deleted SAPL document: {}", fileName);
			var update = new Update(Type.UNPUBLISH, index.get(absoluteFileName), "");
			var newIndex = index.remove(absoluteFileName);
			return Tuples.of(Optional.of(new PrpUpdateEvent(update)), newIndex);
		}

		if (absoluteFilePath.toFile().length() == 0) {
			log.debug("dropping potential duplicate event. {}", kind);
			return Tuples.of(Optional.empty(), index);
		}

		String rawDocument = "";
		SAPL saplDocument = null;
		try {
			rawDocument = readFile(absoluteFilePath);
			saplDocument = interpreter.parse(rawDocument);
		} catch (PolicyEvaluationException | IOException e) {
			throw new RuntimeException(
					"FATAL ERROR: Attempt to publish invalid document. Application shutdown to avoid inconsistent decisions: "
							+ e.getMessage());
		}

		// CREATE or MODIFY events
		// This is very system dependent. Different OS and tools modifying a file can
		// result in different signals.
		// e.g. modification of a file with one editor may result in MODIFY while the
		// other editor results in
		// CREATE without a matching DELETED first.
		// So both signals have to be treated similarly and we have to determine
		// ourselves what happened

		log.debug("Processing directory watch event of kind: {} for {}", kind, absoluteFileName);
		if (index.containsFile(absoluteFileName)) {
			log.debug("the file is already indexed. Treat this as a modification");
			log.info("loading updated SAPL document: {}", fileName);
			var oldDocument = index.get(absoluteFileName);
			var update1 = new Update(Type.UNPUBLISH, oldDocument, "");
			var update2 = new Update(Type.PUBLISH, saplDocument, rawDocument);
			var newIndex = index.put(absoluteFileName, saplDocument);
			return Tuples.of(Optional.of(new PrpUpdateEvent(update1, update2)), newIndex);
		} else {
			log.debug("the file is not yet indexed. Treat this as a file creation.");
			log.info("loading new SAPL document: {}", fileName);
			var update = new Update(Type.PUBLISH, saplDocument, rawDocument);
			var newIndex = index.put(absoluteFileName, saplDocument);
			return Tuples.of(Optional.of(new PrpUpdateEvent(update)), newIndex);
		}
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

	private static class ImmutableFileIndex {
		final Map<String, SAPL> files;

		private ImmutableFileIndex(Map<String, SAPL> newFiles) {
			files = new HashMap<>(newFiles);
		}

		public ImmutableFileIndex put(String absoluteFileName, SAPL saplDocument) {
			var newFiles = new HashMap<>(files);
			newFiles.put(absoluteFileName, saplDocument);
			return new ImmutableFileIndex(newFiles);
		}

		public ImmutableFileIndex remove(String absoluteFileName) {
			var newFiles = new HashMap<>(files);
			newFiles.remove(absoluteFileName);
			return new ImmutableFileIndex(newFiles);
		}

		public SAPL get(String absoluteFileName) {
			return files.get(absoluteFileName);
		}

		public boolean containsFile(String absoluteFileName) {
			return files.containsKey(absoluteFileName);
		}
	}

}
