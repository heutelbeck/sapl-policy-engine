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
package io.sapl.compiler.document;

import io.sapl.ast.SaplDocument;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

/**
 * Represents a parsed SAPL document with its parse tree and its problems.
 *
 * @param sapl the ANTLR parse tree, or null if the document was not parsed
 * @param saplDocument the optimized AST, or null if the document is invalid
 * @param source the original source text
 * @param diagnostics the problems of the document, in the order of their
 * places. Empty if the document is valid.
 */
public record Document(
        @Nullable SaplContext sapl,
        @Nullable SaplDocument saplDocument,
        String source,
        List<Diagnostic> diagnostics) {

    private static final String ERROR_AT_PLACE = "line %d:%d %s";

    /**
     * Keeps the problems as they are reported, whatever the caller does with its
     * list afterwards.
     */
    public Document {
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * @return true if the document has any problem
     */
    public boolean isInvalid() {
        return !diagnostics.isEmpty();
    }

    /**
     * @return the type of the document, as its parse tree shows it, or
     * {@link DocumentType#INVALID} if the document has a problem
     */
    public DocumentType type() {
        if (isInvalid() || sapl == null) {
            return DocumentType.INVALID;
        }
        return switch (sapl.policyElement()) {
        case PolicyOnlyElementContext ignored -> DocumentType.POLICY;
        case PolicySetElementContext ignored  -> DocumentType.POLICY_SET;
        case null, default                    -> DocumentType.INVALID;
        };
    }

    /**
     * Extracts the document name from the parse tree.
     *
     * @return the document name, or null if not available
     */
    public @Nullable String name() {
        if (sapl == null) {
            return null;
        }
        return switch (sapl.policyElement()) {
        case PolicyOnlyElementContext p when p.policy().saplName != null     ->
            unquoteString(p.policy().saplName.getText());
        case PolicySetElementContext ps when ps.policySet().saplName != null ->
            unquoteString(ps.policySet().saplName.getText());
        case null, default                                                   -> null;
        };
    }

    /**
     * Lists every problem of the document on a line of its own: the line and the
     * column where it starts, both counted from 1, and its message.
     *
     * @return the problems, or an empty text if the document is valid
     */
    public String errors() {
        val errors = new StringBuilder();
        for (val diagnostic : diagnostics) {
            val region = diagnostic.region();
            errors.append(ERROR_AT_PLACE.formatted(region.line(), region.column(), diagnostic.message()));
            errors.append('\n');
        }
        return errors.toString();
    }
}
