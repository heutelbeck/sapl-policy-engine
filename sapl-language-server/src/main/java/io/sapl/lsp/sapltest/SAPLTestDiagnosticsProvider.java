/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapltest;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import io.sapl.lsp.core.ParsedDocument;

/**
 * Provides LSP diagnostics for SAPLTest documents based on parse and validation
 * errors.
 */
public class SAPLTestDiagnosticsProvider {

    private static final String SOURCE = "sapltest";

    /**
     * Provides diagnostics for a parsed document.
     *
     * @param document the parsed document
     * @return list of diagnostics
     */
    public List<Diagnostic> provideDiagnostics(ParsedDocument document) {
        var diagnostics = new ArrayList<Diagnostic>();

        // Convert parse errors to diagnostics
        for (var error : document.getParseErrors()) {
            diagnostics.add(createDiagnostic(error.line(), error.charPositionInLine(), error.message(),
                    error.offendingSymbol(), DiagnosticSeverity.Error));
        }

        // Convert validation errors to diagnostics
        for (var error : document.getValidationErrors()) {
            diagnostics.add(createDiagnostic(error.line(), error.charPositionInLine(), error.message(),
                    error.offendingText(), DiagnosticSeverity.Error));
        }

        return diagnostics;
    }

    /**
     * Creates a diagnostic from error information.
     *
     * @param line the line number (1-based from ANTLR)
     * @param charPositionInLine the character position in line (0-based)
     * @param message the error message
     * @param offendingSymbol the offending symbol text
     * @param severity the diagnostic severity
     * @return the diagnostic
     */
    private Diagnostic createDiagnostic(int line, int charPositionInLine, String message, String offendingSymbol,
            DiagnosticSeverity severity) {
        // Convert from ANTLR's 1-based line to LSP's 0-based line
        var startLine = line - 1;
        var endLine   = startLine;
        var startChar = charPositionInLine;
        var endChar   = startChar + (offendingSymbol != null ? offendingSymbol.length() : 1);

        var range = new Range(new Position(startLine, startChar), new Position(endLine, endChar));

        return new Diagnostic(range, message, severity, SOURCE);
    }

}
