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

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokens;

import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.SemanticTokenEncoder;
import io.sapl.test.grammar.antlr.SAPLTestLexer;

/**
 * Provides semantic tokens for syntax highlighting in SAPLTest documents.
 */
public class SAPLTestSemanticTokensProvider {

    /**
     * Provides semantic tokens for a parsed document.
     *
     * @param document the parsed document
     * @return the semantic tokens
     */
    public SemanticTokens provideSemanticTokens(ParsedDocument document) {
        return SemanticTokenEncoder.semanticTokensOf(document, this::mapTokenType);
    }

    /**
     * Maps an ANTLR token to a semantic token type index.
     *
     * @param token the ANTLR token
     * @return the semantic token type index, or -1 if not highlighted
     */
    private int mapTokenType(Token token) {
        var tokenType = token.getType();

        // Skip EOF and hidden tokens
        if (tokenType == Token.EOF) {
            return -1;
        }

        // Map by token type from lexer
        return switch (tokenType) {
        // Comments
        case SAPLTestLexer.ML_COMMENT, SAPLTestLexer.SL_COMMENT -> SAPLTestSemanticTokenTypes.COMMENT;

        // Structure keywords
        case SAPLTestLexer.REQUIREMENT, SAPLTestLexer.SCENARIO, SAPLTestLexer.GIVEN, SAPLTestLexer.WHEN,
                SAPLTestLexer.THEN, SAPLTestLexer.EXPECT,
                SAPLTestLexer.VERIFY                                                                                                                                    ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // Decision keywords (macro styling)
        case SAPLTestLexer.PERMIT, SAPLTestLexer.DENY, SAPLTestLexer.SUSPEND, SAPLTestLexer.INDETERMINATE,
                SAPLTestLexer.NOT_APPLICABLE,
                SAPLTestLexer.DECISION                                                                                                                          ->
            SAPLTestSemanticTokenTypes.MACRO;

        // Combining algorithm keywords (macro styling)
        case SAPLTestLexer.FIRST, SAPLTestLexer.PRIORITY, SAPLTestLexer.UNANIMOUS, SAPLTestLexer.UNIQUE,
                SAPLTestLexer.STRICT, SAPLTestLexer.KW_OR, SAPLTestLexer.ERRORS, SAPLTestLexer.ABSTAIN,
                SAPLTestLexer.PROPAGATE                                                                                                                                                                                  ->
            SAPLTestSemanticTokenTypes.MACRO;

        // Authorization keywords
        case SAPLTestLexer.SUBJECT, SAPLTestLexer.ACTION, SAPLTestLexer.RESOURCE, SAPLTestLexer.ENVIRONMENT,
                SAPLTestLexer.ATTEMPTS, SAPLTestLexer.ON,
                SAPLTestLexer.IN                                                                                                                                        ->
            SAPLTestSemanticTokenTypes.PARAMETER;

        // Mock and setup keywords
        case SAPLTestLexer.FUNCTION, SAPLTestLexer.ATTRIBUTE, SAPLTestLexer.MAPS, SAPLTestLexer.TO, SAPLTestLexer.EMITS,
                SAPLTestLexer.STREAM, SAPLTestLexer.OF, SAPLTestLexer.IS, SAPLTestLexer.CALLED, SAPLTestLexer.ERROR,
                SAPLTestLexer.ONCE, SAPLTestLexer.NEVER,
                SAPLTestLexer.TIMES                                                                                                                                                                                                                                                        ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // Document and configuration keywords
        case SAPLTestLexer.DOCUMENT, SAPLTestLexer.DOCUMENTS, SAPLTestLexer.VARIABLES, SAPLTestLexer.SECRETS,
                SAPLTestLexer.CONFIGURATION,
                SAPLTestLexer.PDP_CONFIGURATION                                                                                                                            ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // Matcher keywords
        case SAPLTestLexer.NULL_KEYWORD, SAPLTestLexer.TEXT, SAPLTestLexer.NUMBER_KEYWORD,
                SAPLTestLexer.BOOLEAN_KEYWORD, SAPLTestLexer.ARRAY, SAPLTestLexer.OBJECT, SAPLTestLexer.WHERE,
                SAPLTestLexer.MATCHING, SAPLTestLexer.ANY, SAPLTestLexer.EQUALS, SAPLTestLexer.CONTAINING,
                SAPLTestLexer.KEY, SAPLTestLexer.VALUE,
                SAPLTestLexer.WITH                                                                                                                                                                                                                                                                                                              ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // String matcher keywords
        case SAPLTestLexer.BLANK, SAPLTestLexer.EMPTY, SAPLTestLexer.NULL_OR_EMPTY, SAPLTestLexer.NULL_OR_BLANK,
                SAPLTestLexer.EQUAL, SAPLTestLexer.COMPRESSED, SAPLTestLexer.WHITESPACE, SAPLTestLexer.CASE_INSENSITIVE,
                SAPLTestLexer.REGEX, SAPLTestLexer.STARTING, SAPLTestLexer.ENDING, SAPLTestLexer.LENGTH,
                SAPLTestLexer.ORDER                                                                                                                                                                                                                                                                                                    ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // Expectation keywords
        case SAPLTestLexer.NEXT, SAPLTestLexer.OBLIGATION, SAPLTestLexer.ADVICE, SAPLTestLexer.OBLIGATIONS ->
            SAPLTestSemanticTokenTypes.KEYWORD;

        // Boolean and special literals
        case SAPLTestLexer.TRUE, SAPLTestLexer.FALSE, SAPLTestLexer.UNDEFINED -> SAPLTestSemanticTokenTypes.KEYWORD;

        // Literals
        case SAPLTestLexer.NUMBER, SAPLTestLexer.INT -> SAPLTestSemanticTokenTypes.NUMBER;
        case SAPLTestLexer.STRING                    -> SAPLTestSemanticTokenTypes.STRING;

        // Identifiers
        case SAPLTestLexer.ID -> SAPLTestSemanticTokenTypes.VARIABLE;

        // Operators
        case SAPLTestLexer.AND -> SAPLTestSemanticTokenTypes.OPERATOR;

        // Not highlighted
        default -> -1;
        };
    }

}
