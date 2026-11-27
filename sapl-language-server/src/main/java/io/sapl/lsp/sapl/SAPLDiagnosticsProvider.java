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

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;

import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
import lombok.val;

/**
 * Provides the diagnostics of the protocol for the problems that the engine
 * reports for a SAPL document.
 */
public class SAPLDiagnosticsProvider {

    private static final String SOURCE = "sapl";

    /**
     * Provides diagnostics for a parsed document.
     *
     * @param document the parsed document
     * @return one diagnostic for each problem of the document, in the order of
     * their places
     */
    public List<Diagnostic> provideDiagnostics(ParsedDocument document) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return List.of();
        }
        val positions = ProtocolPositions.of(saplDocument.getContent());
        return saplDocument.getDiagnostics().stream()
                .map(reported -> published(reported, saplDocument.getUri(), positions)).toList();
    }

    /**
     * The diagnostic that tells a client about one problem of a document: the
     * identifier of its class as the code, the region of the problem as the
     * range, its short form and its explanation as the message, and its further
     * regions as related information. The range marks at least one visible
     * character where the document has one, so that the editor shows it.
     *
     * @param reported the problem as the engine reports it
     * @param uri the URI of the document
     * @param positions the positions of the document
     * @return the diagnostic of the protocol
     */
    static Diagnostic published(io.sapl.compiler.diagnostics.Diagnostic reported, String uri,
            ProtocolPositions positions) {
        val diagnostic     = new Diagnostic(positions.markOf(reported.region()), reported.message(),
                DiagnosticSeverity.Error, SOURCE, reported.identifier());
        val furtherRegions = reported.furtherRegions().stream()
                .map(further -> new DiagnosticRelatedInformation(new Location(uri, positions.rangeOf(further.region())),
                        further.label().text()))
                .toList();
        if (!furtherRegions.isEmpty()) {
            diagnostic.setRelatedInformation(furtherRegions);
        }
        return diagnostic;
    }

}
