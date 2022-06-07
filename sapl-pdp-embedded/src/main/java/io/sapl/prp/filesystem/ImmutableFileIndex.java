/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.common.collect.Maps;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
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

@Slf4j
class ImmutableFileIndex {

	private static final String SAPL_SUFFIX = ".sapl";

	private static final String SAPL_GLOB_PATTERN = "*" + SAPL_SUFFIX;

	private final SAPLInterpreter interpreter;

	private int numberOfInvalidDocuments = 0;

	private int numberOfNameCollisions = 0;

	final List<Update> updates = new LinkedList<>();

	@Getter
	private PrpUpdateEvent updateEvent;

	final Map<String, Document> documentsByPath;

	final Map<String, List<Document>> namesToDocuments;

	public ImmutableFileIndex(String watchDir, SAPLInterpreter interpreter) {
		log.info("Initializing file index for {}", watchDir);

		this.interpreter = interpreter;
		this.documentsByPath = new HashMap<>();
		this.namesToDocuments = new HashMap<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(watchDir), SAPL_GLOB_PATTERN)) {
			for (var filePath : stream) {
				log.debug("loading SAPL document: {}", filePath);
				load(filePath);
			}
		}
		catch (IOException e) {
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
		this.documentsByPath = Maps.newHashMapWithExpectedSize(oldIndex.documentsByPath.size());
		this.namesToDocuments = Maps.newHashMapWithExpectedSize(oldIndex.namesToDocuments.size());
		this.interpreter = oldIndex.interpreter;
		this.numberOfInvalidDocuments = oldIndex.numberOfInvalidDocuments;
		this.numberOfNameCollisions = oldIndex.numberOfNameCollisions;
		for (var entry : oldIndex.documentsByPath.entrySet()) {
			var documentCopy = new Document(entry.getValue());
			this.documentsByPath.put(entry.getKey(), documentCopy);
			addDocumentToNameIndex(documentCopy);
		}
	}

	private void addDocumentToNameIndex(Document document) {
		var documentName = document.getDocumentName();
		var documentsWithName = namesToDocuments.computeIfAbsent(documentName, k -> new LinkedList<>());
		documentsWithName.add(document);
	}

	Document removeDocumentFromMap(String pathOfDocumentToBeRemoved) {
		return documentsByPath.remove(pathOfDocumentToBeRemoved);
	}

	boolean containsDocumentWithPath(String pathOfDocument) {
		return documentsByPath.containsKey(pathOfDocument);
	}

	List<Document> getDocumentByName(String documentName) {
		return namesToDocuments.get(documentName);
	}

	void addWithdrawUpdate(Document oldDocument) {
		log.info("The document was previously published. It will withdrawn from the index.");
		updates.add(new Update(Type.WITHDRAW, oldDocument.getParsedDocument(), oldDocument.getRawDocument()));
	}

	public ImmutableFileIndex afterFileEvent(FileEvent event) {
		var fileName = event.getFile().getName();
		var path = event.getFile().toPath().toAbsolutePath();
		var newIndex = new ImmutableFileIndex(this);
		if (event instanceof FileDeletedEvent) {
			log.info("Unloading deleted SAPL document: {}", fileName);
			newIndex.unload(path);
		}
		else if (event instanceof FileCreatedEvent) {
			log.info("Loading new SAPL document: {}", fileName);
			newIndex.load(path);
		}
		else { // FileChangedEvent
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

	final boolean isConsistent() {
		return numberOfInvalidDocuments == 0 && numberOfNameCollisions == 0;
	}

	final boolean isInconsistent() {
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
		documentsByPath.put(newDocument.getAbsolutePath(), newDocument);
		if (!newDocument.isValid()) {
			numberOfInvalidDocuments++;
			return;
		}
		List<Document> documentsWithName;
		if (namesToDocuments.containsKey(newDocument.getDocumentName())) {
			documentsWithName = namesToDocuments.get(newDocument.getDocumentName());
		}
		else {
			documentsWithName = new LinkedList<>();
			namesToDocuments.put(newDocument.getDocumentName(), documentsWithName);
		}
		documentsWithName.add(newDocument);
		if (documentsWithName.size() == 1) {
			log.debug("The document has been parsed successfully. It will be published to the index.");
			newDocument.setPublished(true);
			updates.add(new Update(Type.PUBLISH, newDocument.getParsedDocument(), newDocument.getRawDocument()));
		}
		else {
			log.warn(
					"The document has been parsed successfully but it resulted in a name collision: '{}'. The document will not be published.",
					newDocument.getDocumentName());
			numberOfNameCollisions++;
		}
	}

	void change(Path filePath) {
		unload(filePath);
		load(filePath);
	}

	String getAbsolutePathAsString(Path filePath) {
		return filePath.toAbsolutePath().toString();
	}

	void unload(Path filePath) {
		var path = getAbsolutePathAsString(filePath);

		if (!containsDocumentWithPath(path))
			return;

		var oldDocument = removeDocumentFromMap(path);
		if (oldDocument.isPublished())
			addWithdrawUpdate(oldDocument);

		if (!oldDocument.isValid()) {
			numberOfInvalidDocuments--;
			return;
		}

		var documentsWithOriginalName = getDocumentByName(oldDocument.getDocumentName());
		if (documentsWithOriginalName.size() > 1)
			numberOfNameCollisions--;

		documentsWithOriginalName.remove(oldDocument);

		if (documentsWithOriginalName.size() == 1) {
			var onlyRemainingDocumentWithName = documentsWithOriginalName.get(0);
			if (!onlyRemainingDocumentWithName.isPublished()) {
				log.info(
						"The removal of the document resolved a name collision. As a result, the document in file '{}' named '{}' will be published.",
						onlyRemainingDocumentWithName.getPath().getFileName(),
						onlyRemainingDocumentWithName.getDocumentName());
				updates.add(new Update(Type.PUBLISH, onlyRemainingDocumentWithName.getParsedDocument(),
						onlyRemainingDocumentWithName.getRawDocument()));
				onlyRemainingDocumentWithName.setPublished(true);
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
				rawDocument = Files.readString(path);
			}
			catch (IOException e) {
				log.debug("Error reading file '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
						e.getMessage());
			}
			try {
				if (rawDocument != null) {
					parsedDocument = interpreter.parse(rawDocument);
					documentName = parsedDocument.getPolicyElement().getSaplName();
				}
			}
			catch (PolicyEvaluationException e) {
				log.debug("Error in document '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
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
