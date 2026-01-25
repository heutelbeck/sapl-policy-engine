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

import static io.sapl.compiler.util.StringsUtil.unquoteString;

import java.util.List;

import io.sapl.ast.SaplDocument;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.validation.ValidationError;
import lombok.val;

/**
 * Represents a parsed SAPL document with its parse tree and validation status.
 *
 * @param sapl the ANTLR parse tree
 * @param saplDocument optimized AST
 * @param source the original source text
 * @param syntaxErrors list of syntax errors from parsing
 * @param validationErrors list of semantic validation errors
 * @param errorMessage combined error message if any errors exist
 */
public record Document(
        SaplContext sapl,
        SaplDocument saplDocument,
        String source,
        List<String> syntaxErrors,
        List<ValidationError> validationErrors,
        String errorMessage) {

    /**
     * Returns true if the document has any syntax or validation errors.
     */
    public boolean isInvalid() {
        return !syntaxErrors.isEmpty() || !validationErrors.isEmpty();
    }

    /**
     * Returns the document type based on the parse tree content.
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
    public String name() {
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

    public String errors() {
        val errors = new StringBuilder();
        if (errorMessage != null) {
            errors.append(errorMessage);
            errors.append('\n');
        }
        if (syntaxErrors != null) {
            for (val syntaxError : syntaxErrors) {
                errors.append(syntaxError);
                errors.append('\n');
            }
        }
        if (validationErrors != null) {
            for (val validationError : validationErrors) {
                errors.append(validationError);
                errors.append('\n');
            }
        }
        return errors.toString();
    }
}
