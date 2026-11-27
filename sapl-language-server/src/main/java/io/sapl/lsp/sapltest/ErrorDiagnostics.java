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
package io.sapl.lsp.sapltest;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import io.sapl.lsp.core.ProtocolPositions;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument.ParseError;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument.ValidationError;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Turns the parse and validation errors of a SAPL test document, as ANTLR and
 * the validator of the test language report them, into diagnostics of the
 * language server protocol.
 */
@UtilityClass
class ErrorDiagnostics {

    /**
     * @param content the text of the document
     * @param parseErrors the errors of the lexer and the parser
     * @param validationErrors the errors of the validation
     * @param source the name of the language, shown as the source of each
     * diagnostic
     * @return one diagnostic for each parse error and each validation error, in
     * this order
     */
    static List<Diagnostic> diagnosticsOf(String content, List<ParseError> parseErrors,
            List<ValidationError> validationErrors, String source) {
        val diagnostics = new ArrayList<Diagnostic>();
        val positions   = ProtocolPositions.of(content);

        parseErrors.stream().map(error -> diagnosticOf(positions, error.line(), error.charPositionInLine(),
                error.message(), error.offendingSymbol(), source)).forEach(diagnostics::add);

        validationErrors.stream().map(error -> diagnosticOf(positions, error.line(), error.charPositionInLine(),
                error.message(), error.offendingText(), source)).forEach(diagnostics::add);

        return diagnostics;
    }

    /**
     * An error names its place in the numbers of ANTLR: a line that ends only at
     * a line feed, counted from 1, and the code points from its start. The
     * diagnostic spans the offending text, and at least one character, so that
     * the editor shows it.
     */
    private static Diagnostic diagnosticOf(ProtocolPositions positions, int line, int charPositionInLine,
            String message, String offendingText, String source) {
        val start  = positions.positionOfParserPosition(line, charPositionInLine);
        val length = (offendingText == null || offendingText.isEmpty()) ? 1 : offendingText.length();
        val end    = new Position(start.getLine(), start.getCharacter() + length);
        return new Diagnostic(new Range(start, end), message, DiagnosticSeverity.Error, source);
    }

}
