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
package io.sapl.test.utils;

import java.nio.file.Files;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DocumentHelper {
    public static Document readSaplDocument(final String saplDocumentName, final SAPLInterpreter saplInterpreter) {
        if (saplDocumentName == null || saplDocumentName.isEmpty()) {
            return null;
        }

        final var filename = constructFileEnding(saplDocumentName);
        return saplInterpreter.parseDocument(findFileOnClasspath(filename));
    }

    public static Document readSaplDocumentFromInputString(final String input, final SAPLInterpreter saplInterpreter) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        return saplInterpreter.parseDocument(input);
    }

    private static String constructFileEnding(final String filename) {
        if (filename.endsWith(".sapl")) {
            return filename;
        } else {
            return filename + ".sapl";
        }
    }

    @SneakyThrows
    public static String findFileOnClasspath(final String filename) {
        final var path = ClasspathHelper.findPathOnClasspath(DocumentHelper.class.getClassLoader(), filename);

        return Files.readString(path);
    }
}
