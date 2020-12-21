package io.sapl.prp.filesystem;

import java.util.*;

import io.sapl.grammar.sapl.SAPL;

class ImmutableFileIndex {

    final int invalidDocuments;
    final int nameCollisions;
    final Map<String, Optional<SAPL>> filesToDocument;
    final Map<String, List<String>> namesToFiles;

    ImmutableFileIndex(Map<String, Optional<SAPL>> newFiles) {
        filesToDocument = new HashMap<>(newFiles);
        namesToFiles = new HashMap<>(newFiles.size());
        var collisionCount = 0;
        var invalidCount = 0;
        for(var entry : newFiles.entrySet()) {
            var fileName = entry.getKey();
            var document = entry.getValue();
            if(document.isEmpty()) {
                invalidCount++;
            } else {
                var documentName = document.get().getPolicyElement().getSaplName();
                if(!namesToFiles.containsKey(documentName)) {
                    namesToFiles.put(documentName, new LinkedList<>());
                }
                var filesWithDocumentsMatchingName = namesToFiles.get(documentName);
                if(!filesWithDocumentsMatchingName.isEmpty()) {
                    collisionCount++;
                }
                filesWithDocumentsMatchingName.add(fileName);
            }
        }
        nameCollisions = collisionCount;
        invalidDocuments = invalidCount;
    }

    public ImmutableFileIndex put(String absoluteFileName, Optional<SAPL> saplDocument) {
        var newFiles = new HashMap<>(filesToDocument);
        newFiles.put(absoluteFileName, saplDocument);
        return new ImmutableFileIndex(newFiles);
    }

    public ImmutableFileIndex remove(String absoluteFileName) {
        var newFiles = new HashMap<>(filesToDocument);
        newFiles.remove(absoluteFileName);
        return new ImmutableFileIndex(newFiles);
    }

    public Optional<SAPL> get(String absoluteFileName) {
        return filesToDocument.get(absoluteFileName);
    }

    public boolean containsFile(String absoluteFileName) {
        return filesToDocument.containsKey(absoluteFileName);
    }

    public boolean isConsistent() {
        return invalidDocuments == 0 &&  nameCollisions==0;
    }

    public boolean isInconsistent() {
        return !isConsistent();
    }

    public boolean becameConsistentComparedTo(ImmutableFileIndex idx) {
        return idx.isInconsistent() && isConsistent();
    }

    public boolean becameInconsistentComparedTo(ImmutableFileIndex idx) {
        return idx.isConsistent() && isInconsistent();
    }
}
