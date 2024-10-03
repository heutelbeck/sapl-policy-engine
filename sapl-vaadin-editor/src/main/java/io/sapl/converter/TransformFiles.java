/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

/**
 * Tool to convert JS files from AMD to ESM format during build. It also injects
 * configurationId communication code.
 */
@UtilityClass
public class TransformFiles {

    private static final String FRONTEND_FOLDER_PATH          = "/META-INF/frontend/";
    private static final String SAPL_MODE_FILENAME            = "sapl-mode.js";
    private static final String SAPL_TEST_MODE_FILENAME       = "sapl-test-mode.js";
    private static final String XTEXT_CODEMIRROR_FILENAME     = "xtext-codemirror.js";
    private static final String XTEXT_CODEMIRROR_FILENAME_NEW = "xtext-codemirror-patched.js";

    /**
     * Entry point for conversion.
     *
     * @param args command line parameters
     * @throws IOException in case of conversion errors
     */
    public void main(String[] args) throws IOException {
        var classPathDir     = new File(TransformFiles.class.getResource("/").getPath());
        var targetFolderPath = classPathDir + FRONTEND_FOLDER_PATH;

        convertFileToESM(targetFolderPath + SAPL_MODE_FILENAME, SaplModeConverter::convertToESM);
        convertFileToESM(targetFolderPath + SAPL_TEST_MODE_FILENAME, SaplModeConverter::convertToESM);
        convertFileToESM(targetFolderPath + XTEXT_CODEMIRROR_FILENAME, XtextCodemirrorConverter::convertToESM);
        addConfigIdData(targetFolderPath + XTEXT_CODEMIRROR_FILENAME, targetFolderPath + XTEXT_CODEMIRROR_FILENAME_NEW);
    }

    private void convertFileToESM(String filePath, Converter converter) throws IOException {
        Path     path    = Paths.get(filePath);
        String   content = Files.readString(path);
        String[] terms   = content.trim().split("\\s+");
        if (!"import".equals(terms[0])) {
            String result = converter.convert(content);
            // first remove the byte order mark, then convert to UTF-8
            Files.writeString(path, result.replaceFirst("^\uFEFF", ""));
        }
    }

    private void addConfigIdData(String filePath, String filePathNew) throws IOException {

        Path       newPath    = Paths.get(filePathNew);
        FileReader fileReader = new FileReader(filePath);

        try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            List<String> terms = new ArrayList<String>();
            String       term  = null;

            String payload00 = """

                    import { saplPdpConfigurationId } from "./sapl-editor"; // Hello SAPL!
                    """;
            String payload01 = """
                        if (requestUrl.indexOf('?') >= 0)
                            requestUrl += '&configurationId=' + saplPdpConfigurationId;
                        else
                            requestUrl += '?configurationId=' + saplPdpConfigurationId;
                    """;

            int line = 0;
            while (null != (term = bufferedReader.readLine())) {
                if (274 == line)
                    terms.add(term + payload01);
                else
                    terms.add(term);
                line++;
            }
            terms.set(0, terms.get(0) + payload00);
            System.out.println(payload00);
            String result = terms.stream().collect(Collectors.joining("\n"));
            Files.writeString(newPath, result);
        }
    }

    private interface Converter {
        String convert(String content);
    }
}
