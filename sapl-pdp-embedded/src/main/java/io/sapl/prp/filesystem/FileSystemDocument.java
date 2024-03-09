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
package io.sapl.prp.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
class FileSystemDocument {

    Path path;

    Document document;

    boolean published;

    public FileSystemDocument(Path path, SAPLInterpreter interpreter) {
        this.path = path;
        try {
            document = interpreter.parseDocument(Files.newInputStream(path));
            if (document == null || document.isInvalid()) {
                log.warn("Error in document '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
                        document);
            }
        } catch (IOException e) {
            log.warn("Error reading file '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
                    e.getMessage());
        }
    }

    public FileSystemDocument(FileSystemDocument document) {
        this.path      = document.path;
        this.published = document.published;
        this.document  = document.document;
    }

    public String getAbsolutePath() {
        return path.toAbsolutePath().toString();
    }

    public String getDocumentName() {
        if (document == null)
            return null;

        return document.name();
    }

    public boolean isInvalid() {
        return document == null || document.isInvalid();
    }

}
