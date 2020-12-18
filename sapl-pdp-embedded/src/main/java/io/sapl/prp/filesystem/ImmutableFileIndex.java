package io.sapl.prp.filesystem;

import java.util.HashMap;
import java.util.Map;

import io.sapl.grammar.sapl.SAPL;

class ImmutableFileIndex {
    final Map<String, SAPL> files;

    ImmutableFileIndex(Map<String, SAPL> newFiles) {
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
