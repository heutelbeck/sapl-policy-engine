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

import org.eclipse.lsp4j.TextEdit;

import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
import lombok.val;

/**
 * Provides document formatting for SAPL documents.
 * Formats only when the syntax of the document has no problem (problems of
 * its rules are acceptable).
 */
class SAPLFormattingProvider {

    /**
     * Provides formatting edits for a SAPL document.
     * Returns an empty list if the syntax of the document has a problem.
     *
     * @param document the parsed document
     * @return list of text edits to apply
     */
    List<TextEdit> provideFormatting(ParsedDocument document) {
        if (!(document instanceof SAPLParsedDocument saplDocument) || !saplDocument.isSyntaxValid()) {
            return List.of();
        }

        val visitor   = new SAPLFormattingVisitor(saplDocument.getTokenStream());
        val formatted = visitor.visitSapl(saplDocument.getSaplParseTree());

        val range = ProtocolPositions.of(document.getContent()).rangeOfDocument();
        return List.of(new TextEdit(range, formatted));
    }

}
