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
package io.sapl.lsp.sapl.completion;

import java.util.List;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;

import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;

/**
 * Analyzes cursor context to determine what type of completions should be
 * offered. Used primarily to detect when the cursor is after a function call
 * or attribute access, enabling schema-based property completions.
 * <p/>
 * For example, when the cursor is at:
 * <ul>
 * <li>{@code time.now().} - detects FUNCTION context with
 * functionName="time.now"</li>
 * <li>{@code subject.<pip.attr>.} - detects ATTRIBUTE context with
 * functionName="pip.attr"</li>
 * <li>{@code |<pip.attr>.} - detects ENVIRONMENT_ATTRIBUTE context</li>
 * </ul>
 */
@UtilityClass
public class ContextAnalyzer {

    /**
     * Types of proposals based on cursor context.
     */
    public enum ProposalType {
        /** After a regular attribute access: `expr.<attr>` */
        ATTRIBUTE,
        /** After an environment attribute: `|<attr>` */
        ENVIRONMENT_ATTRIBUTE,
        /** After a function call: `func()` */
        FUNCTION,
        /** General expression context - variables or function names */
        VARIABLE_OR_FUNCTION_NAME,
        /** Cannot determine context */
        INDETERMINATE
    }

    /**
     * Result of context analysis.
     *
     * @param prefix the full expression prefix collected from cursor position
     * @param ctxPrefix the immediate prefix (what user typed after last separator)
     * @param functionName the function or attribute name if type is
     * FUNCTION/ATTRIBUTE
     * @param type the detected proposal type
     */
    public record ContextAnalysisResult(String prefix, String ctxPrefix, String functionName, ProposalType type) {

        /** Creates an indeterminate result for when analysis fails. */
        public static ContextAnalysisResult indeterminate(String prefix) {
            return new ContextAnalysisResult(prefix, prefix, "", ProposalType.INDETERMINATE);
        }

        /** Creates a result for general variable/function name context. */
        public static ContextAnalysisResult variableOrFunction(String prefix, String ctxPrefix) {
            return new ContextAnalysisResult(prefix, ctxPrefix, "", ProposalType.VARIABLE_OR_FUNCTION_NAME);
        }
    }

    /**
     * Analyzes the cursor context to determine what type of completions to offer.
     *
     * @param tokens the token stream from the document
     * @param position the cursor position (0-indexed line and column)
     * @return analysis result containing proposal type and function/attribute name
     */
    public static ContextAnalysisResult analyze(List<Token> tokens, Position position) {
        var line   = position.getLine() + 1; // Tokens use 1-based lines
        var column = position.getCharacter();

        // Find token at or before cursor
        Token tokenAtCursor = null;
        Token previousToken = null;
        Token twoTokensBack = null;

        for (var token : tokens) {
            if (token.getType() == Token.EOF) {
                break;
            }
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                var tokenEndLine   = token.getLine();
                var tokenEndColumn = token.getCharPositionInLine() + token.getText().length();

                if (tokenEndLine < line || (tokenEndLine == line && tokenEndColumn <= column)) {
                    twoTokensBack = previousToken;
                    previousToken = tokenAtCursor;
                    tokenAtCursor = token;
                } else {
                    break;
                }
            }
        }

        if (tokenAtCursor == null) {
            return ContextAnalysisResult.indeterminate("");
        }

        // Build context prefix from current typing position
        var ctxPrefix = buildCtxPrefix(tokenAtCursor, line, column);

        // Check for function call: look for closing paren followed by dot
        if (tokenAtCursor.getType() == SAPLLexer.DOT && previousToken != null
                && previousToken.getType() == SAPLLexer.RPAREN) {
            // Cursor at `function().` - look for function name
            var functionName = extractFunctionNameBeforeParen(tokens, previousToken);
            if (!functionName.isEmpty()) {
                return new ContextAnalysisResult("", ctxPrefix, functionName, ProposalType.FUNCTION);
            }
        }

        // Check for attribute step: look for closing angle bracket followed by dot
        if (tokenAtCursor.getType() == SAPLLexer.DOT && previousToken != null
                && previousToken.getType() == SAPLLexer.GT) {
            // Cursor at `.<attr>.` - look for attribute name
            var result = extractAttributeNameBeforeGT(tokens, previousToken);
            if (!result.name().isEmpty()) {
                return new ContextAnalysisResult("", ctxPrefix, result.name(), result.type());
            }
        }

        // Check if we're at a dot after an identifier (partial expression)
        if (tokenAtCursor.getType() == SAPLLexer.DOT) {
            // Just after a dot - could be property access or function/attribute start
            return ContextAnalysisResult.variableOrFunction("", "");
        }

        // Check if typing after a dot (identifier after dot)
        if (previousToken != null && previousToken.getType() == SAPLLexer.DOT) {
            // Typing after dot - check what's before the dot
            if (twoTokensBack != null && twoTokensBack.getType() == SAPLLexer.RPAREN) {
                var functionName = extractFunctionNameBeforeParen(tokens, twoTokensBack);
                if (!functionName.isEmpty()) {
                    return new ContextAnalysisResult("", ctxPrefix, functionName, ProposalType.FUNCTION);
                }
            }
            if (twoTokensBack != null && twoTokensBack.getType() == SAPLLexer.GT) {
                var result = extractAttributeNameBeforeGT(tokens, twoTokensBack);
                if (!result.name().isEmpty()) {
                    return new ContextAnalysisResult("", ctxPrefix, result.name(), result.type());
                }
            }
        }

        // Default to variable/function name context
        var prefix = buildFullPrefix(tokenAtCursor, previousToken, twoTokensBack);
        return ContextAnalysisResult.variableOrFunction(prefix, ctxPrefix);
    }

    /**
     * Builds the immediate prefix from cursor position.
     */
    private static String buildCtxPrefix(Token token, int line, int column) {
        if (token.getType() == SAPLLexer.ID) {
            var tokenStart = token.getCharPositionInLine();
            if (token.getLine() == line && column > tokenStart) {
                var endIdx = column - tokenStart;
                var text   = token.getText();
                return text.substring(0, Math.min(endIdx, text.length()));
            }
        }
        return "";
    }

    /**
     * Extracts the function name before a closing paren.
     * Walks backward through tokens to find: ID ( DOT ID )* LPAREN
     */
    private static String extractFunctionNameBeforeParen(List<Token> tokens, Token rparenToken) {
        var rparenIndex = findTokenIndex(tokens, rparenToken);
        if (rparenIndex < 0) {
            return "";
        }

        // Find matching LPAREN
        var parenDepth  = 1;
        var lparenIndex = -1;
        for (var i = rparenIndex - 1; i >= 0 && parenDepth > 0; i--) {
            var token = tokens.get(i);
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                if (token.getType() == SAPLLexer.RPAREN) {
                    parenDepth++;
                } else if (token.getType() == SAPLLexer.LPAREN) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        lparenIndex = i;
                    }
                }
            }
        }

        if (lparenIndex < 0) {
            return "";
        }

        // Collect function name before LPAREN: ID ( DOT ID )*
        return collectIdentifierChain(tokens, lparenIndex - 1);
    }

    private record AttributeResult(String name, ProposalType type) {}

    /**
     * Extracts the attribute name before a closing angle bracket.
     * Handles both regular attributes `.<attr>` and environment attributes
     * `|<attr>`.
     */
    private static AttributeResult extractAttributeNameBeforeGT(List<Token> tokens, Token gtToken) {
        var gtIndex = findTokenIndex(tokens, gtToken);
        if (gtIndex < 0) {
            return new AttributeResult("", ProposalType.INDETERMINATE);
        }

        // Look backward for LT or PIPE_LT
        var type    = ProposalType.ATTRIBUTE;
        var ltIndex = -1;

        for (var i = gtIndex - 1; i >= 0; i--) {
            var token = tokens.get(i);
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                if (token.getType() == SAPLLexer.LT) {
                    ltIndex = i;
                    type    = ProposalType.ATTRIBUTE;
                    break;
                }
                if (token.getType() == SAPLLexer.PIPE_LT) {
                    ltIndex = i;
                    type    = ProposalType.ENVIRONMENT_ATTRIBUTE;
                    break;
                }
            }
        }

        if (ltIndex < 0) {
            return new AttributeResult("", ProposalType.INDETERMINATE);
        }

        // Collect attribute name between LT and GT
        var nameBuilder = new StringBuilder();
        for (var i = ltIndex + 1; i < gtIndex; i++) {
            var token = tokens.get(i);
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                var tokenType = token.getType();
                // Attribute name can be: ID (DOT ID)* optionally with (args)
                if (tokenType == SAPLLexer.ID || tokenType == SAPLLexer.DOT) {
                    nameBuilder.append(token.getText());
                } else if (tokenType == SAPLLexer.LPAREN) {
                    // Stop at arguments
                    break;
                }
            }
        }

        return new AttributeResult(nameBuilder.toString(), type);
    }

    /**
     * Collects an identifier chain (ID.ID.ID) walking backward from given index.
     * Stops at any non-identifier/dot token (e.g., RPAREN from previous call).
     */
    private static String collectIdentifierChain(List<Token> tokens, int startIndex) {
        var chain    = new StringBuilder();
        var expectId = true;

        for (var i = startIndex; i >= 0; i--) {
            var token = tokens.get(i);
            if (token.getChannel() == Token.HIDDEN_CHANNEL) {
                continue;
            }
            var tokenType = token.getType();
            if (expectId && tokenType == SAPLLexer.ID) {
                chain.insert(0, token.getText());
                expectId = false;
            } else if (!expectId && tokenType == SAPLLexer.DOT) {
                // Only add the dot if there's more identifier chain before it
                var nextNonHidden = findNextNonHiddenToken(tokens, i - 1);
                if (nextNonHidden != null && nextNonHidden.getType() == SAPLLexer.ID) {
                    chain.insert(0, ".");
                    expectId = true;
                } else {
                    // Stop here - the dot leads to a non-identifier (like RPAREN)
                    break;
                }
            } else {
                break;
            }
        }

        return chain.toString();
    }

    /**
     * Finds the next non-hidden token at or before the given index.
     */
    private static Token findNextNonHiddenToken(List<Token> tokens, int startIndex) {
        for (var i = startIndex; i >= 0; i--) {
            var token = tokens.get(i);
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                return token;
            }
        }
        return null;
    }

    /**
     * Finds the index of a token in the token list.
     */
    private static int findTokenIndex(List<Token> tokens, Token target) {
        for (var i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds a full prefix from recent tokens for general completion filtering.
     */
    private static String buildFullPrefix(Token current, Token prev, Token twoPrev) {
        var prefix = new StringBuilder();

        if (current != null && current.getType() == SAPLLexer.ID) {
            if (prev != null && prev.getType() == SAPLLexer.DOT) {
                if (twoPrev != null && twoPrev.getType() == SAPLLexer.ID) {
                    prefix.append(twoPrev.getText()).append('.').append(current.getText());
                } else {
                    prefix.append('.').append(current.getText());
                }
            } else {
                prefix.append(current.getText());
            }
        }

        return prefix.toString();
    }

}
