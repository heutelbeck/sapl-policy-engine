/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides document formatting for SAPL documents.
 * Formats only when there are no parse errors (validation errors are
 * acceptable).
 */
class SAPLFormattingProvider {

    /**
     * Provides formatting edits for a SAPL document.
     * Returns an empty list if the document has parse errors.
     *
     * @param document the parsed document
     * @return list of text edits to apply
     */
    List<TextEdit> provideFormatting(ParsedDocument document) {
        if (!document.getParseErrors().isEmpty()) {
            return List.of();
        }

        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return List.of();
        }

        val visitor   = new SAPLFormattingVisitor(saplDocument.getTokenStream());
        val formatted = visitor.visitSapl(saplDocument.getSaplParseTree());

        val content  = document.getContent();
        val lines    = content.split("\n", -1);
        val lastLine = lines.length - 1;
        val lastChar = lines[lastLine].length();

        val range = new Range(new Position(0, 0), new Position(lastLine, lastChar));
        return List.of(new TextEdit(range, formatted));
    }

}
