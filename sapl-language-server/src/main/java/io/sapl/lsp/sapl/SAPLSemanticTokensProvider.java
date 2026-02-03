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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokens;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.lsp.core.ParsedDocument;

/**
 * Provides semantic tokens for syntax highlighting in SAPL documents.
 */
public class SAPLSemanticTokensProvider {

    /**
     * Provides semantic tokens for a parsed document.
     *
     * @param document the parsed document
     * @return the semantic tokens
     */
    public SemanticTokens provideSemanticTokens(ParsedDocument document) {
        var tokens        = document.getTokens();
        var semanticInfos = new ArrayList<SemanticTokenInfo>();

        for (var token : tokens) {
            var tokenType = mapTokenType(token);
            if (tokenType >= 0) {
                semanticInfos.add(new SemanticTokenInfo(token.getLine() - 1, // Convert to 0-based
                        token.getCharPositionInLine(), token.getText().length(), tokenType, 0));
            }
        }

        // Sort by position (line, then column)
        semanticInfos
                .sort(Comparator.comparingInt(SemanticTokenInfo::line).thenComparingInt(SemanticTokenInfo::column));

        // Encode as LSP semantic tokens data array
        var data = encodeTokens(semanticInfos);
        return new SemanticTokens(data);
    }

    /**
     * Maps an ANTLR token to a semantic token type index.
     *
     * @param token the ANTLR token
     * @return the semantic token type index, or -1 if not highlighted
     */
    private int mapTokenType(Token token) {
        var tokenType = token.getType();
        var text      = token.getText();

        // Skip EOF and hidden tokens
        if (tokenType == Token.EOF) {
            return -1;
        }

        // Check for entitlements and algorithms (special macro styling)
        if (SAPLSemanticTokenTypes.ENTITLEMENTS_AND_ALGORITHMS.contains(text)) {
            return SAPLSemanticTokenTypes.MACRO;
        }

        // Map by token type from lexer
        return switch (tokenType) {
        // Comments (on hidden channel but still in token list)
        case SAPLLexer.ML_COMMENT, SAPLLexer.SL_COMMENT -> SAPLSemanticTokenTypes.COMMENT;

        // Keywords
        case SAPLLexer.IMPORT, SAPLLexer.AS, SAPLLexer.SET, SAPLLexer.FOR, SAPLLexer.POLICY, SAPLLexer.PERMIT,
                SAPLLexer.DENY, SAPLLexer.VAR, SAPLLexer.SCHEMA, SAPLLexer.ENFORCED, SAPLLexer.OBLIGATION,
                SAPLLexer.ADVICE, SAPLLexer.TRANSFORM, SAPLLexer.TRUE, SAPLLexer.FALSE, SAPLLexer.NULL,
                SAPLLexer.UNDEFINED, SAPLLexer.IN,
                SAPLLexer.EACH                                                                                                                                                                                                                                                                                                                      ->
            SAPLSemanticTokenTypes.KEYWORD;

        // Combining algorithm keywords
        case SAPLLexer.FIRST, SAPLLexer.PRIORITY, SAPLLexer.UNANIMOUS, SAPLLexer.UNIQUE, SAPLLexer.KW_OR,
                SAPLLexer.ERRORS, SAPLLexer.ABSTAIN,
                SAPLLexer.PROPAGATE                                                                                                                                ->
            SAPLSemanticTokenTypes.MACRO;

        // Subscription elements as parameters
        case SAPLLexer.SUBJECT, SAPLLexer.ACTION, SAPLLexer.RESOURCE, SAPLLexer.ENVIRONMENT ->
            SAPLSemanticTokenTypes.PARAMETER;

        // Operators
        case SAPLLexer.FILTER, SAPLLexer.SUBTEMPLATE, SAPLLexer.OR, SAPLLexer.AND, SAPLLexer.BITOR, SAPLLexer.BITXOR,
                SAPLLexer.BITAND, SAPLLexer.EQ, SAPLLexer.NEQ, SAPLLexer.REGEX, SAPLLexer.LT, SAPLLexer.LE,
                SAPLLexer.GT, SAPLLexer.GE, SAPLLexer.PLUS, SAPLLexer.MINUS, SAPLLexer.STAR, SAPLLexer.SLASH,
                SAPLLexer.PERCENT, SAPLLexer.NOT, SAPLLexer.AT, SAPLLexer.HASH, SAPLLexer.DOT, SAPLLexer.DOTDOT,
                SAPLLexer.PIPE_LT                                                                                                                                                                                                                                                                                                                                                                                                  ->
            SAPLSemanticTokenTypes.OPERATOR;

        // Literals
        case SAPLLexer.NUMBER -> SAPLSemanticTokenTypes.NUMBER;
        case SAPLLexer.STRING -> SAPLSemanticTokenTypes.STRING;

        // Identifiers need context analysis for function vs variable
        // For now, treat all as variables
        case SAPLLexer.ID -> SAPLSemanticTokenTypes.VARIABLE;

        // Not highlighted
        default -> -1;
        };
    }

    /**
     * Encodes semantic tokens as the LSP data array format.
     * Each token is encoded as 5 integers: deltaLine, deltaStartChar, length,
     * tokenType, tokenModifiers.
     *
     * @param tokens the semantic token infos
     * @return the encoded data array
     */
    private List<Integer> encodeTokens(List<SemanticTokenInfo> tokens) {
        var data     = new ArrayList<Integer>();
        var prevLine = 0;
        var prevChar = 0;

        for (var token : tokens) {
            var deltaLine = token.line() - prevLine;
            var deltaChar = deltaLine == 0 ? token.column() - prevChar : token.column();

            data.add(deltaLine);
            data.add(deltaChar);
            data.add(token.length());
            data.add(token.tokenType());
            data.add(token.tokenModifiers());

            prevLine = token.line();
            prevChar = token.column();
        }

        return data;
    }

    /**
     * Represents semantic token information before encoding.
     */
    private record SemanticTokenInfo(int line, int column, int length, int tokenType, int tokenModifiers) {}

}
