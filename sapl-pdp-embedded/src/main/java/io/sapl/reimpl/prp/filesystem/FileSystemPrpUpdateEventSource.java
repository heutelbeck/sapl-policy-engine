package io.sapl.reimpl.prp.filesystem;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEventSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public class FileSystemPrpUpdateEventSource implements PrpUpdateEventSource {

	private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";
	private static final Pattern POLICY_FILE_REGEX_PATTERN = Pattern.compile(".+\\.sapl");

	private final SAPLInterpreter interpreter;

	@Override
	public Flux<PrpUpdateEvent> getUpdates() {
		// TODO Auto-generated method stub
		return null;
	}

	private PrpUpdateEvent loadAllFilesIntoIntitialEvent(String path) {
//		try {
//			try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(path), POLICY_FILE_GLOB_PATTERN)) {
//				for (Path filePath : stream) {
//					log.info("load: {}", filePath);
//					final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
//					parsedDocIdx.put(filePath.toString(), saplDocument);
//				}
//			}
//			parsedDocIdx.setLiveMode();
//		} catch (IOException | PolicyEvaluationException e) {
//			log.error("Error while initializing the document index.", e);
//		}
		return null;
	}

	public static class ImmutableFileIndex {
		final Map<String, SAPL> files;

		public ImmutableFileIndex() {
			files = new HashMap<>();
		}

		private ImmutableFileIndex(Map<String, SAPL> newFiles) {
			files = newFiles;
		}

	}

}
