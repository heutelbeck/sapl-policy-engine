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

import io.sapl.api.model.DocumentLines;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * What the checks of a document know about its tokens: the kinds of tokens the
 * language has, where a token stands, and the tokens that stand in for a
 * mistake.
 * <p>
 * A token addresses the document by the indices of code points, as the lexer
 * counts them. A region addresses it by offsets, so the lines of the document
 * translate between the two.
 */
@UtilityClass
class Tokens {

    /**
     * A text of the document that several problems quote is quoted up to this
     * number of bytes of UTF-8 only. A document must not make its report many
     * times as long as itself.
     */
    private static final int MAX_REPEATED_QUOTE_BYTES = 100;

    private static final Set<Integer> KEYWORDS = Set.of(SAPLLexer.IMPORT, SAPLLexer.AS, SAPLLexer.SET, SAPLLexer.POLICY,
            SAPLLexer.PERMIT, SAPLLexer.DENY, SAPLLexer.SUSPEND, SAPLLexer.VAR, SAPLLexer.SCHEMA, SAPLLexer.ENFORCED,
            SAPLLexer.OBLIGATION, SAPLLexer.ADVICE, SAPLLexer.TRANSFORM, SAPLLexer.TRUE, SAPLLexer.FALSE,
            SAPLLexer.NULL, SAPLLexer.UNDEFINED, SAPLLexer.IN, SAPLLexer.HAS, SAPLLexer.ANY, SAPLLexer.ALL,
            SAPLLexer.EACH, SAPLLexer.FOR);

    private static final Set<Integer> KEYWORDS_USABLE_AS_NAMES = Set.of(SAPLLexer.SUBJECT, SAPLLexer.ACTION,
            SAPLLexer.RESOURCE, SAPLLexer.ENVIRONMENT, SAPLLexer.KW_OR, SAPLLexer.ERRORS, SAPLLexer.ABSTAIN,
            SAPLLexer.PROPAGATE, SAPLLexer.FIRST, SAPLLexer.PRIORITY, SAPLLexer.STRICT, SAPLLexer.UNANIMOUS,
            SAPLLexer.UNIQUE);

    private static final Set<Integer> LITERALS = Set.of(SAPLLexer.NUMBER, SAPLLexer.TRUE, SAPLLexer.FALSE,
            SAPLLexer.NULL, SAPLLexer.UNDEFINED);

    private static final Set<Integer> STARTS_A_PART = Set.of(SAPLLexer.IMPORT, SAPLLexer.SCHEMA, SAPLLexer.SET,
            SAPLLexer.POLICY, SAPLLexer.OBLIGATION, SAPLLexer.ADVICE, SAPLLexer.TRANSFORM);

    private static final Set<Integer> STARTS_A_POLICY_PART = Set.of(SAPLLexer.VAR, SAPLLexer.OBLIGATION,
            SAPLLexer.ADVICE, SAPLLexer.TRANSFORM, SAPLLexer.POLICY);

    private static final Set<Integer> STARTS_A_VALUE_BESIDES_NAMES = Set.of(SAPLLexer.LBRACE, SAPLLexer.AT,
            SAPLLexer.HASH, SAPLLexer.NOT, SAPLLexer.PIPE_LT);

    private static final Set<Integer> ENDS_AN_EXPRESSION_BESIDES_VALUES = Set.of(SAPLLexer.RBRACE, SAPLLexer.GT,
            SAPLLexer.AT, SAPLLexer.HASH, SAPLLexer.STAR);

    /**
     * The kinds of brackets, with the tokens and the texts that open and close
     * them.
     */
    enum Bracket {
        PARENTHESIS(SAPLLexer.LPAREN, SAPLLexer.RPAREN, "(", ")"),
        SQUARE_BRACKET(SAPLLexer.LBRACKET, SAPLLexer.RBRACKET, "[", "]"),
        BRACE(SAPLLexer.LBRACE, SAPLLexer.RBRACE, "{", "}");

        private final int    openingType;
        private final int    closingType;
        private final String opening;
        private final String closing;

        Bracket(int openingType, int closingType, String opening, String closing) {
            this.openingType = openingType;
            this.closingType = closingType;
            this.opening     = opening;
            this.closing     = closing;
        }

        int closingType() {
            return closingType;
        }

        String opening() {
            return opening;
        }

        String closing() {
            return closing;
        }

        /**
         * @return the kind of bracket a token opens, or null if it opens none
         */
        static @Nullable Bracket openedBy(Token token) {
            for (val bracket : values()) {
                if (bracket.openingType == token.getType()) {
                    return bracket;
                }
            }
            return null;
        }

        /**
         * @return the kind of bracket a token closes, or null if it closes none
         */
        static @Nullable Bracket closedBy(Token token) {
            for (val bracket : values()) {
                if (bracket.closingType == token.getType()) {
                    return bracket;
                }
            }
            return null;
        }
    }

    /**
     * @return whether the token is a keyword that is a name only in backticks
     */
    static boolean isKeyword(int type) {
        return KEYWORDS.contains(type);
    }

    /**
     * @return whether the token is a keyword that is a name as well: the
     * elements of the authorization subscription and the words of a combining
     * algorithm
     */
    static boolean isKeywordUsableAsName(int type) {
        return KEYWORDS_USABLE_AS_NAMES.contains(type);
    }

    /**
     * @return whether a token names an element of the authorization
     * subscription
     */
    static boolean isSubscriptionElement(int type) {
        return switch (type) {
        case SAPLLexer.SUBJECT, SAPLLexer.ACTION, SAPLLexer.RESOURCE, SAPLLexer.ENVIRONMENT -> true;
        default                                                                             -> false;
        };
    }

    /**
     * @return whether the token is a name or a key: an identifier, a string, or
     * a keyword that is a name as well
     */
    static boolean isName(int type) {
        return type == SAPLLexer.ID || type == SAPLLexer.BACKTICK_ID || type == SAPLLexer.STRING
                || isKeywordUsableAsName(type);
    }

    /**
     * @return whether the token is written as a word: an identifier or a keyword
     */
    static boolean isWord(int type) {
        return type == SAPLLexer.ID || isKeyword(type) || isKeywordUsableAsName(type);
    }

    /**
     * @return whether the token can be part of a path written without spaces,
     * such as {@code subject.name}
     */
    static boolean isPartOfPath(int type) {
        return type == SAPLLexer.DOT || type == SAPLLexer.DOTDOT || isName(type);
    }

    /**
     * @return whether a value can end with the token, so that a step can follow
     * it directly
     */
    static boolean endsAValue(int type) {
        return type == SAPLLexer.RBRACKET || type == SAPLLexer.RPAREN || isName(type);
    }

    /**
     * @return whether an expression can end with the token
     */
    static boolean endsAnExpression(int type) {
        return LITERALS.contains(type) || endsAValue(type) || ENDS_AN_EXPRESSION_BESIDES_VALUES.contains(type);
    }

    /**
     * @return whether a value can start with the token and the token cannot
     * continue a value before it
     */
    static boolean startsAValue(int type) {
        return LITERALS.contains(type) || isName(type) || STARTS_A_VALUE_BESIDES_NAMES.contains(type);
    }

    /**
     * @return whether a part of the document starts at the token that is checked
     * on its own: an import, a schema, a policy, an obligation, an advice or a
     * transformation
     */
    static boolean startsAPart(int type) {
        return STARTS_A_PART.contains(type);
    }

    /**
     * @return whether the token starts a part of a policy that follows its
     * conditions, or a new policy
     */
    static boolean startsAPolicyPart(int type) {
        return STARTS_A_POLICY_PART.contains(type);
    }

    /**
     * @return whether the second token follows the first without any character
     * between them
     */
    static boolean adjacent(Token first, Token second) {
        return first.getStopIndex() + 1 == second.getStartIndex();
    }

    /**
     * @return the offset of the first character of the token
     */
    static int startOf(Token token, DocumentLines lines) {
        return lines.offsetOfCodePoint(Math.max(0, token.getStartIndex()));
    }

    /**
     * @return the offset after the last character of the token. A token that
     * takes no space ends where it starts.
     */
    static int endOf(Token token, DocumentLines lines) {
        val start = Math.max(0, token.getStartIndex());
        return lines.offsetOfCodePoint(Math.max(start, token.getStopIndex() + 1));
    }

    /**
     * @return the region of the text of the token. The end of the document has
     * no text, so its region is empty.
     */
    static Region regionOf(Token token, DocumentLines lines) {
        return Region.of(lines, startOf(token, lines), endOf(token, lines));
    }

    /**
     * @return the region from the start of the first token to the end of the
     * last one
     */
    static Region regionOf(Token first, Token last, DocumentLines lines) {
        return Region.of(lines, startOf(first, lines), endOf(last, lines));
    }

    /**
     * @return the empty region at a place of the document
     */
    static Region placeAt(int offset, DocumentLines lines) {
        return Region.of(lines, offset, offset);
    }

    /**
     * @return the text of the document between two offsets
     */
    static String textOf(DocumentLines lines, int start, int end) {
        return lines.text().substring(start, end);
    }

    /**
     * A token that stands in for a mistake, as the token that was meant.
     *
     * @param first the first token of the mistake
     * @param last the last token of the mistake
     * @param type the type of the token that was meant
     * @param text the text of the token that was meant, or null for the text
     * of the mistake
     * @return the token, which takes the space of the mistake
     */
    static CommonToken standIn(Token first, Token last, int type, @Nullable String text) {
        val token = new CommonToken(first);
        token.setType(type);
        token.setChannel(Token.DEFAULT_CHANNEL);
        token.setStopIndex(last.getStopIndex());
        token.setText(text);
        return token;
    }

    /**
     * A token that is added to correct the document. It takes no space in the
     * document.
     *
     * @param near a token next to the place
     * @param codePointIndex the index of the code point before which the token
     * is added
     * @param type the type of the token
     * @param text the text of the token
     * @return the token
     */
    static CommonToken added(Token near, int codePointIndex, int type, String text) {
        val token = new CommonToken(near);
        token.setType(type);
        token.setChannel(Token.DEFAULT_CHANNEL);
        token.setStartIndex(codePointIndex);
        token.setStopIndex(codePointIndex - 1);
        token.setText(text);
        return token;
    }

    /**
     * A token that the parser does not read, kept for the editor.
     *
     * @param token the token
     * @param type the type it is shown as
     * @return the token on the hidden channel
     */
    static CommonToken hidden(Token token, int type) {
        val hidden = new CommonToken(token);
        hidden.setType(type);
        hidden.setChannel(Token.HIDDEN_CHANNEL);
        return hidden;
    }

    /**
     * The text of the path that ends directly before a token: names, keys and
     * dots that are written without space between them.
     *
     * @param tokens tokens that the parser reads, in the order of the document
     * @param count the number of the tokens, from the first, that the path can
     * take
     * @param following the token before which the path ends
     * @param lines the lines of the document
     * @return the path, or the empty text if there is none or it is too long to
     * quote
     */
    static String pathBefore(List<? extends Token> tokens, int count, Token following, DocumentLines lines) {
        var start = count;
        var next  = following;
        while (start > 0) {
            val part = tokens.get(start - 1);
            if (!isPartOfPath(part.getType()) || !adjacent(part, next)) {
                break;
            }
            // A code point takes at least one byte of UTF-8.
            if (following.getStartIndex() - part.getStartIndex() > MAX_REPEATED_QUOTE_BYTES) {
                return "";
            }
            start--;
            next = part;
        }
        return quotable(lines, startOf(next, lines), startOf(following, lines));
    }

    /**
     * @return the text between two offsets, or the empty text if it is longer
     * than a text that several problems quote
     */
    static String quotable(DocumentLines lines, int start, int end) {
        // A code unit of UTF-16 takes at least one byte of UTF-8, so a text that is
        // short enough in bytes is short enough in code units.
        if (end - start > MAX_REPEATED_QUOTE_BYTES) {
            return "";
        }
        return quotable(textOf(lines, start, end));
    }

    /**
     * @return the text, or the empty text if it is longer than a text that
     * several problems quote
     */
    static String quotable(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length > MAX_REPEATED_QUOTE_BYTES ? "" : text;
    }

}
