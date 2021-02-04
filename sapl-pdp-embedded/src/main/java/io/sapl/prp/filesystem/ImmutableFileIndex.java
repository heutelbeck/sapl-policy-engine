package io.sapl.prp.filesystem;

import com.google.common.collect.Maps;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileEvent;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.sapl.util.filemonitoring.FileMonitorUtil.readFile;

@Slf4j
class ImmutableFileIndex {

	private static final String SAPL_SUFFIX = ".sapl";
	private static final String SAPL_GLOB_PATTERN = "*" + SAPL_SUFFIX;

	private final SAPLInterpreter interpreter;

	private int invalidDocuments = 0;
	private int nameCollisions = 0;
	List<Update> updates = new LinkedList<>();

	@Getter
	private PrpUpdateEvent updateEvent;

	final Map<String, Document> pathToDocuments;
	final Map<String, List<Document>> namesToDocuments;

	public ImmutableFileIndex(String watchDir, SAPLInterpreter interpreter) {
		log.info("Initializing file index for {}", watchDir);

		this.interpreter = interpreter;
		this.pathToDocuments = new HashMap<>();
		this.namesToDocuments = new HashMap<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(watchDir), SAPL_GLOB_PATTERN)) {
			for (var filePath : stream) {
				log.info("loading SAPL document: {}", filePath);
				load(filePath);
			}
		} catch (IOException e) {
			log.error("Unable to open the directory containing policies: {}", watchDir);
			updates.add(new Update(Type.INCONSISTENT, null, null));
			updateEvent = new PrpUpdateEvent(updates);
			return;
		}

		if (isInconsistent()) {
			log.warn("The initial set of documents is inconsistent!");
			updates.add(new Update(Type.INCONSISTENT, null, null));
		}

		updateEvent = new PrpUpdateEvent(updates);
	}

	private ImmutableFileIndex(ImmutableFileIndex oldIndex) {
		this.pathToDocuments = Maps.newHashMapWithExpectedSize(oldIndex.pathToDocuments.size());
		this.namesToDocuments = Maps.newHashMapWithExpectedSize(oldIndex.namesToDocuments.size());
		this.interpreter = oldIndex.interpreter;
		this.invalidDocuments = oldIndex.invalidDocuments;
		this.nameCollisions = oldIndex.nameCollisions;
		for (var entry : oldIndex.pathToDocuments.entrySet()) {
			var documentCopy = new Document(entry.getValue());
			this.pathToDocuments.put(entry.getKey(), documentCopy);
			addDocumentToNameIndex(documentCopy);
		}
	}

	private void addDocumentToNameIndex(Document document) {
		var documentName = document.getDocumentName();
		var documentsWithName = namesToDocuments.computeIfAbsent(documentName, k -> new LinkedList<>());
		documentsWithName.add(document);
	}

	public ImmutableFileIndex afterFileEvent(FileEvent event) {
		var fileName = event.getFile().getName();
		var path = event.getFile().toPath().toAbsolutePath();
		var newIndex = new ImmutableFileIndex(this);
		if (event instanceof FileDeletedEvent) {
			log.info("Unloading deleted SAPL document: {}", fileName);
			newIndex.unload(path);
		} else if (event instanceof FileCreatedEvent) {
			log.info("Loading new SAPL document: {}", fileName);
			newIndex.load(path);
		} else { // FileChangedEvent
			log.info("Loading updated SAPL document: {}", fileName);
			newIndex.change(path);
		}

		if (newIndex.becameConsistentComparedTo(this)) {
			log.info("The set of documents was previously INCONSISTENT and is now CONSISTENT again.");
			newIndex.updates.add(new Update(Type.CONSISTENT, null, null));
		}
		if (newIndex.becameInconsistentComparedTo(this)) {
			log.warn("The set of documents was previously CONSISTENT and is now INCONSISTENT.");
			newIndex.updates.add(new Update(Type.INCONSISTENT, null, null));
		}

		newIndex.updateEvent = new PrpUpdateEvent(newIndex.updates);

		return newIndex;
	}

	private boolean isConsistent() {
		return invalidDocuments == 0 && nameCollisions == 0;
	}

	private boolean isInconsistent() {
		return !isConsistent();
	}

	public boolean becameConsistentComparedTo(ImmutableFileIndex idx) {
		return idx.isInconsistent() && isConsistent();
	}

	public boolean becameInconsistentComparedTo(ImmutableFileIndex idx) {
		return idx.isConsistent() && isInconsistent();
	}

	final void load(Path filePath) {
		var newDocument = new Document(filePath, interpreter);
		pathToDocuments.put(newDocument.getAbsolutePath(), newDocument);
		if (!newDocument.isValid()) {
			invalidDocuments++;
			return;
		}
		List<Document> documentsWithName;
		if (namesToDocuments.containsKey(newDocument.getDocumentName())) {
			documentsWithName = namesToDocuments.get(newDocument.getDocumentName());
		} else {
			documentsWithName = new LinkedList<>();
			namesToDocuments.put(newDocument.getDocumentName(), documentsWithName);
		}
		documentsWithName.add(newDocument);
		if (documentsWithName.size() == 1) {
			log.info("The document has been parsed successfully. It will be published to the index.");
			newDocument.setPublished(true);
			updates.add(new Update(Type.PUBLISH, newDocument.getParsedDocument(), newDocument.getRawDocument()));
		} else {
			log.warn(
					"The document has been parsed successfully but it resulted in a name collision: '{}'. The document will not be published.",
					newDocument.getDocumentName());
			nameCollisions++;
		}
	}

	void change(Path filePath) {
		unload(filePath);
		load(filePath);
	}

	void unload(Path filePath) {
		var path = filePath.toAbsolutePath().toString();
		if (pathToDocuments.containsKey(path)) {
			var oldDocument = pathToDocuments.remove(path);
			if (oldDocument.isPublished()) {
				log.info("The document was previously published. It will unpublished from the index.");
				updates.add(new Update(Type.UNPUBLISH, oldDocument.getParsedDocument(), oldDocument.getRawDocument()));
			}
			if (oldDocument.isValid()) {
				var documentsWithOriginalName = namesToDocuments.get(oldDocument.getDocumentName());
				if (documentsWithOriginalName.size() > 1) {
					nameCollisions--;
				}
				documentsWithOriginalName.remove(oldDocument);
				if (documentsWithOriginalName.size() == 1) {
					var onlyRemaingDocumentWithName = documentsWithOriginalName.get(0);
					if (!onlyRemaingDocumentWithName.isPublished()) {
						log.info(
								"The removal of the document resolved a name collision. As a result, the document in file '{}' named '{}' will be published.",
								onlyRemaingDocumentWithName.getPath().getFileName(),
								onlyRemaingDocumentWithName.getDocumentName());
						updates.add(new Update(Type.PUBLISH, onlyRemaingDocumentWithName.getParsedDocument(),
								onlyRemaingDocumentWithName.getRawDocument()));
						onlyRemaingDocumentWithName.setPublished(true);
					}
				}
			} else {
				invalidDocuments--;
			}
		}
	}

	@Data
	@Slf4j
	static class Document {
		Path path;
		String rawDocument;
		SAPL parsedDocument;
		String documentName;
		boolean published;

		public Document(Path path, SAPLInterpreter interpreter) {
			this.path = path;
			try {
				rawDocument = readFile(path.toFile());
			} catch (IOException e) {
				log.debug("Error reading file '{}': {}. Will lead to inconsistent index.", getAbsolutePath(),
						e.getMessage());
			}
			try {
				if (rawDocument != null) {
					parsedDocument = interpreter.parse(rawDocument);
					documentName = parsedDocument.getPolicyElement().getSaplName();
				}
			} catch (PolicyEvaluationException e) {
				log.debug("Error in document '{}': {}. Will lead to inconsistent index.", getAbsolutePath(),
						e.getMessage());
			}
		}

		public Document(Document document) {
			this.path = document.path;
			this.published = document.published;
			this.rawDocument = document.rawDocument;
			this.parsedDocument = document.parsedDocument;
			this.documentName = document.documentName;
		}

		public String getAbsolutePath() {
			return path.toAbsolutePath().toString();
		}

		public boolean isValid() {
			return parsedDocument != null;
		}
	}

}
