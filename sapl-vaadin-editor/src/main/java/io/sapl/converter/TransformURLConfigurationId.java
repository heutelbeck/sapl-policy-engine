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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Tool to convert Query URL to include ConfigurationID.
 */
@UtilityClass
public class TransformURLConfigurationId {

    private static final String FRONTEND_FOLDER_PATH           = "/META-INF/frontend/";
    private static final String XTEXT_CODEMIRROR_FILENAME_FROM = "xtext-codemirror.js";
    private static final String XTEXT_CODEMIRROR_FILENAME_TO   = "xtext-codemirror_sapl.js";

    /**
     * Entry point for conversion.
     *
     * @param args command line parameters
     * @throws IOException in case of conversion errors
     */
    public void transform() throws IOException {
        var classPathDir     = new File(TransformURLConfigurationId.class.getResource("/").getPath());
        var targetFolderPath = classPathDir + FRONTEND_FOLDER_PATH;

        convertURLConfigurationId(targetFolderPath + XTEXT_CODEMIRROR_FILENAME_FROM,
                targetFolderPath + XTEXT_CODEMIRROR_FILENAME_TO);
    }

    private void convertURLConfigurationId(String filePathFrom, String filePathTo) throws IOException {
        Path   pathTo   = Paths.get(filePathTo);
        Path   pathFrom = Paths.get(filePathFrom);
        String content  = Files.readString(pathFrom);

        if (true) {
            String result = convertURL(content);
            // first remove the byte order mark, then convert to UTF-8
            Files.writeString(pathTo, result.replaceFirst("^\uFEFF", ""));
        }
    }

    /**
     * @param JS code of Xtext service subscriber
     * @return code with altered URL
     */
    public String convertURL(String code) {
        final String        namedDefineRegex = "var requestUrl = self._requestUrl;";
        final Pattern       pattern          = Pattern.compile(namedDefineRegex);
        final Matcher       matcher          = pattern.matcher(code);
        final StringBuilder builder          = new StringBuilder(code);

        final String addition = """
                if (true) {
                	if (requestUrl.indexOf('?') >= 0)
                		requestUrl += '&configurationId=1';
                	else
                		requestUrl += '?configurationId=1';
                }
                      """;

        matcher.matches();
        var first = matcher.results().findFirst();

        if (first.isPresent()) {
            builder.insert(first.get().end(), "\n" + addition);
        }
        return builder.toString();
    }

    private interface Converter {
        String convert(String content);
    }
}
