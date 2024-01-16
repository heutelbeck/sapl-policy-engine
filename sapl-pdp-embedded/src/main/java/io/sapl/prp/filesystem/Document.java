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

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
class Document {

    Path path;

    String rawDocument;

    SAPL parsedDocument;

    String documentName;

    boolean published;

    public Document(Path path, SAPLInterpreter interpreter) {
        this.path = path;
        try {
            rawDocument = Files.readString(path);
        } catch (IOException e) {
            log.warn("Error reading file '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
                    e.getMessage());
        }
        try {
            if (rawDocument != null) {
                parsedDocument = interpreter.parse(rawDocument);
                documentName   = parsedDocument.getPolicyElement().getSaplName();
            }
        } catch (PolicyEvaluationException e) {
            log.warn("Error in document '{}': {}. Will lead to inconsistent index.", path.toAbsolutePath(),
                    e.getMessage());
        }
    }

    public Document(Document document) {
        this.path           = document.path;
        this.published      = document.published;
        this.rawDocument    = document.rawDocument;
        this.parsedDocument = document.parsedDocument;
        this.documentName   = document.documentName;
    }

    public String getAbsolutePath() {
        return path.toAbsolutePath().toString();
    }

    public boolean isInvalid() {
        return parsedDocument == null;
    }

}
