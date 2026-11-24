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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
import lombok.val;

/**
 * Offers the quick fixes of the problems of a SAPL document as code actions of
 * the protocol: one for each problem that has a quick fix. The titles belong to
 * the catalogue of problems.
 */
class SAPLCodeActionProvider {

    private static final String TITLE_INSERT  = "Insert '%s'";
    private static final String TITLE_REMOVE  = "Remove '%s'";
    private static final String TITLE_REPLACE = "Replace with '%s'";

    private static final Comparator<Position> IN_THE_DOCUMENT = Comparator.comparingInt(Position::getLine)
            .thenComparingInt(Position::getCharacter);

    /**
     * Provides the quick fixes for the problems whose marked range touches a
     * range of the document.
     *
     * @param document the parsed document
     * @param requested the range the client asks for, such as the cursor or a
     * selection
     * @return one code action for each problem with a quick fix whose range
     * touches the requested range
     */
    List<CodeAction> provideCodeActions(ParsedDocument document, Range requested) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return List.of();
        }
        val positions = ProtocolPositions.of(saplDocument.getContent());
        return saplDocument.getDiagnostics().stream()
                .flatMap(reported -> offerFor(reported, requested, saplDocument, positions).stream()).toList();
    }

    private static Optional<CodeAction> offerFor(Diagnostic reported, Range requested, SAPLParsedDocument document,
            ProtocolPositions positions) {
        val quickFix = reported.quickFix();
        if (quickFix == null || !touches(positions.markOf(reported.region()), requested)) {
            return Optional.empty();
        }
        val edit       = new TextEdit(positions.rangeOf(quickFix.region()), quickFix.replacement());
        val codeAction = new CodeAction(titleOf(quickFix, document.getContent()));
        codeAction.setKind(CodeActionKind.QuickFix);
        codeAction.setDiagnostics(List.of(SAPLDiagnosticsProvider.published(reported, document.getUri(), positions)));
        codeAction.setIsPreferred(true);
        codeAction.setEdit(new WorkspaceEdit(Map.of(document.getUri(), List.of(edit))));
        return Optional.of(codeAction);
    }

    /**
     * Whether two ranges overlap or meet. Meeting counts, so that a cursor at
     * either end of a marked region gets the quick fix, and a marked region that
     * is empty gets one at all.
     */
    private static boolean touches(Range one, Range other) {
        return IN_THE_DOCUMENT.compare(one.getStart(), other.getEnd()) <= 0
                && IN_THE_DOCUMENT.compare(other.getStart(), one.getEnd()) <= 0;
    }

    /**
     * What a quick fix does, as a short imperative. A quick fix that inserts
     * replaces an empty region. What it inserts can start with a space that
     * separates it from the text before, which the title leaves out.
     *
     * @param quickFix the quick fix
     * @param content the text of the document
     * @return the title of the code action
     */
    private static String titleOf(QuickFix quickFix, String content) {
        val replaced    = content.substring(quickFix.region().start(), quickFix.region().end());
        val replacement = quickFix.replacement();
        if (replaced.isEmpty()) {
            return TITLE_INSERT.formatted(replacement.strip());
        }
        if (replacement.isEmpty()) {
            return TITLE_REMOVE.formatted(replaced);
        }
        return TITLE_REPLACE.formatted(replacement);
    }

}
