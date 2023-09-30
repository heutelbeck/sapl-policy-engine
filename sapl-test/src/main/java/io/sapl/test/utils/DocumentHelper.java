package io.sapl.test.utils;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import java.io.IOException;
import java.nio.file.Files;
import lombok.experimental.UtilityClass;
import reactor.core.Exceptions;

@UtilityClass
public class DocumentHelper {
    public static SAPL readSaplDocument(final String saplDocumentName, final SAPLInterpreter saplInterpreter) {
        if(saplDocumentName == null || saplDocumentName.isEmpty()) {
            return null;
        }

        final var filename = constructFileEnding(saplDocumentName);

        return saplInterpreter.parse(findFileOnClasspath(filename));
    }

    private static String constructFileEnding(final String filename) {
        if (filename.endsWith(".sapl")) {
            return filename;
        }
        else {
            return filename + ".sapl";
        }
    }

    private static String findFileOnClasspath(final String filename) {
        final var path = ClasspathHelper.findPathOnClasspath(DocumentHelper.class.getClassLoader(), filename);
        try {
            return Files.readString(path);
        }
        catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }
}
