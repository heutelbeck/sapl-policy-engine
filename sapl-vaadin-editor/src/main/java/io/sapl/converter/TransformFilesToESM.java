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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.experimental.UtilityClass;

/**
 * Tool to convert JS files from AMD to ESM format during build.
 */
@UtilityClass
public class TransformFilesToESM {

    private static final String FRONTEND_FOLDER_PATH      = "/META-INF/frontend/";
    private static final String SAPL_MODE_FILENAME        = "sapl-mode.js";
    private static final String SAPL_TEST_MODE_FILENAME   = "sapl-test-mode.js";
    private static final String XTEXT_CODEMIRROR_FILENAME = "xtext-codemirror.js";

    /**
     * Entry point for conversion.
     *
     * @param args command line parameters
     * @throws IOException in case of conversion errors
     */
    public void main(String[] args) throws IOException {
        var classPathDir     = new File(TransformFilesToESM.class.getResource("/").getPath());
        var targetFolderPath = classPathDir + FRONTEND_FOLDER_PATH;

        convertFileToESM(targetFolderPath + SAPL_MODE_FILENAME, SaplModeConverter::convertToESM);
        convertFileToESM(targetFolderPath + SAPL_TEST_MODE_FILENAME, SaplModeConverter::convertToESM);
        convertFileToESM(targetFolderPath + XTEXT_CODEMIRROR_FILENAME, XtextCodemirrorConverter::convertToESM);
    }

    private void convertFileToESM(String filePath, Converter converter) throws IOException {
        Path     path    = Paths.get(filePath);
        String   content = Files.readString(path);
        String[] terms   = content.trim().split("\\s+");
        if (!"import".equals(terms[0])) {
            String result = converter.convert(content);
            // first remove the byte order mark, then convert to UTF-8
            Files.write(path, result.replaceFirst("^\uFEFF", "").getBytes(StandardCharsets.UTF_8));
        }
    }

    private interface Converter {
        String convert(String content);
    }
}
