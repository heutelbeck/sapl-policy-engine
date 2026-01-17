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
package io.sapl.compiler.ast;

import io.sapl.ast.SaplDocument;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.validation.ValidationError;

import java.util.List;

/**
 * Represents a parsed SAPL document with its parse tree and validation status.
 *
 * @param id the document identifier
 * @param name the document name (extracted from policy/set name)
 * @param sapl the ANTLR parse tree
 * @param saplDocument optimized AST
 * @param source the original voterMetadata text
 * @param syntaxErrors list of syntax errors from parsing
 * @param validationErrors list of semantic validation errors
 * @param errorMessage combined errors message if any errors exist
 */
public record Document(
        String id,
        String name,
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

}
