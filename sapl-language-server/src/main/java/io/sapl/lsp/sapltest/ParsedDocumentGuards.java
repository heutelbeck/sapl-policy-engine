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

import static io.sapl.compiler.util.TrojanSourceUtil.isTrojanSourceCharacter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.DocumentLines;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument.ParseError;

import lombok.val;

/**
 * Pre-parse guards of the SAPL-test parsed documents. The editor compiles
 * arbitrary, untrusted text on every keystroke, so the same protections the
 * bundle/compile path enforces must apply here. Unlike that path, which rejects
 * the document outright, the editor surfaces each problem as an error
 * diagnostic so the author can see and fix it. SAPL documents get these
 * protections from the analysis of the engine.
 */
final class ParsedDocumentGuards {

    private static final int    MAX_TROJAN_DIAGNOSTICS = 100;
    private static final String ERROR_NESTING_TOO_DEEP = "Document nesting is too deep to parse.";
    private static final String ERROR_TOO_LARGE        = "Document exceeds the maximum size of %d bytes.";
    private static final String ERROR_TROJAN           = "Bidirectional unicode control character detected (possible trojan source).";

    private ParsedDocumentGuards() {
    }

    static boolean exceedsMaxLength(String content) {
        return content.getBytes(StandardCharsets.UTF_8).length > DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES;
    }

    static ParseError nestingTooDeep() {
        return new ParseError(1, 0, ERROR_NESTING_TOO_DEEP, "");
    }

    /**
     * Diagnostics that can be determined before parsing: oversized input and
     * bidirectional control characters. Returns an empty list for clean input.
     */
    static List<ParseError> preParseDiagnostics(String content) {
        val diagnostics = new ArrayList<ParseError>();
        if (exceedsMaxLength(content)) {
            diagnostics
                    .add(new ParseError(1, 0, ERROR_TOO_LARGE.formatted(DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES), ""));
        }
        addTrojanDiagnostics(content, diagnostics);
        return diagnostics;
    }

    private static void addTrojanDiagnostics(String content, List<ParseError> diagnostics) {
        val offsets = bidiControlOffsets(content);
        if (offsets.isEmpty()) {
            return;
        }
        // A parse error names its place as the parser does, like the errors of the
        // parser itself.
        val lines = DocumentLines.of(content);
        for (val offset : offsets) {
            diagnostics.add(new ParseError(lines.parserLineOf(offset), lines.charPositionInLineOf(offset), ERROR_TROJAN,
                    String.valueOf(content.charAt(offset))));
        }
    }

    private static List<Integer> bidiControlOffsets(String content) {
        val limit   = Math.min(content.length(), DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES);
        val offsets = new ArrayList<Integer>();
        for (var offset = 0; offset < limit && offsets.size() < MAX_TROJAN_DIAGNOSTICS; offset++) {
            if (isTrojanSourceCharacter(content.charAt(offset))) {
                offsets.add(offset);
            }
        }
        return offsets;
    }
}
