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
package io.sapl.lsp.core;

import java.util.List;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Interface for parsed documents supporting multiple grammar types.
 * Provides access to parse results independent of the specific grammar.
 */
public interface ParsedDocument {

    /**
     * Gets the document URI.
     *
     * @return the URI
     */
    String getUri();

    /**
     * Gets the document content.
     *
     * @return the raw content
     */
    String getContent();

    /**
     * Gets the parse tree root.
     *
     * @return the parse tree, or null if parsing failed completely
     */
    ParseTree getParseTree();

    /**
     * Gets all tokens from the document.
     *
     * @return list of tokens
     */
    List<Token> getTokens();

    /**
     * Checks if the document has any parse or validation errors.
     *
     * @return true if there are errors
     */
    boolean hasErrors();

    /**
     * Gets parse errors from lexer/parser.
     *
     * @return list of parse errors
     */
    List<ParseError> getParseErrors();

    /**
     * Gets semantic validation errors.
     *
     * @return list of validation errors
     */
    List<ValidationError> getValidationErrors();

    /**
     * Represents a parse error with location information.
     *
     * @param line the line number (1-based)
     * @param charPositionInLine the character position in line (0-based)
     * @param message the error message
     * @param offendingSymbol the offending symbol text
     */
    record ParseError(int line, int charPositionInLine, String message, String offendingSymbol) {}

    /**
     * Represents a semantic validation error.
     *
     * @param line the line number (1-based)
     * @param charPositionInLine the character position in line (0-based)
     * @param message the error message
     * @param offendingText the offending text
     */
    record ValidationError(int line, int charPositionInLine, String message, String offendingText) {}

}
