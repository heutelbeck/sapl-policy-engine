package io.sapl.converter;

import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@UtilityClass
public class TransformFilesToESM {

    private final String FRONTEND_FOLDER_PATH = "/META-INF/frontend/";

    private final String SAPL_MODE_FILENAME = "sapl-mode.js";

    private final String XTEXT_CODEMIRROR_FILENAME = "xtext-codemirror.js";

    public void main(String[] args) {
        var classPathDir = new File(TransformFilesToESM.class.getResource("/").getPath());

        String targetFolderPath = classPathDir + FRONTEND_FOLDER_PATH;

        convertFileToESM(targetFolderPath + SAPL_MODE_FILENAME, SaplModeConverter::convertToESM);
        convertFileToESM(targetFolderPath + XTEXT_CODEMIRROR_FILENAME, XtextCodemirrorConverter::convertToESM);
    }

    private void convertFileToESM(String filePath, Converter converter) {
        File file = new File(filePath);

        if (file.isFile()) {
            try {
                String content = Files.readString(file.toPath());
                String[] terms = content.trim().split("\\s+");
                if (!terms[0].equals("import")) {
                    String result = converter.convert(content);
                    Files.write(file.toPath(), result.getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private interface Converter {
        String convert(String content);
    }

}
