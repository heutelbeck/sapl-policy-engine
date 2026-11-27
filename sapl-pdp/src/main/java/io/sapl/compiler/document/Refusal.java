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
import io.sapl.compiler.document.Tokens.Bracket;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The place where the parser refused a token: the tokens around it and the
 * tokens the parser would have accepted instead. The accepted tokens tell where
 * in the grammar the parser was.
 *
 * @param read the tokens the parser read
 * @param refusedIndex the index of the refused token among them, or their
 * number at the end of the document
 * @param expected what the parser would have accepted instead
 * @param context the rule the parser was in where it first left a construct for
 * the refused token
 */
record Refusal(ReadTokens read, int refusedIndex, Expected expected, ParserRuleContext context) {

    private static final int[] DELIMITERS = { SAPLLexer.SEMI, SAPLLexer.RPAREN, SAPLLexer.RBRACKET, SAPLLexer.RBRACE };

    /**
     * What an index of a token is where there is no such token.
     */
    private static final int NONE = -1;

    /**
     * The tokens the parser read, with what the checks of a refusal ask of all
     * of them. It is found once for the document, so that no refusal searches
     * through the document.
     *
     * @param tokens the tokens, without the end of the document
     * @param lines the lines of the document
     * @param firstPolicySet the index of the first token {@code set}, or the
     * number of tokens if there is none
     * @param innermostOpenBracket for the index of each token, and for the end of
     * the document, the index of the innermost bracket that is open before it
     */
    record ReadTokens(
            List<? extends Token> tokens,
            DocumentLines lines,
            int firstPolicySet,
            List<Integer> innermostOpenBracket) {

        /**
         * @param tokens the tokens, without the end of the document
         * @param lines the lines of the document
         * @return the tokens with what the checks of a refusal ask of them
         */
        static ReadTokens of(List<? extends Token> tokens, DocumentLines lines) {
            var firstPolicySet = tokens.size();
            val innermost      = new ArrayList<Integer>(tokens.size() + 1);
            val open           = new ArrayDeque<Integer>();
            for (val token : tokens) {
                val index = innermost.size();
                innermost.add(Objects.requireNonNullElse(open.peek(), NONE));
                if (token.getType() == SAPLLexer.SEMI) {
                    open.clear();
                } else if (token.getType() == SAPLLexer.SET) {
                    firstPolicySet = Math.min(firstPolicySet, index);
                } else if (Bracket.openedBy(token) != null) {
                    open.push(index);
                } else if (Bracket.closedBy(token) != null && !open.isEmpty()) {
                    open.pop();
                }
            }
            innermost.add(Objects.requireNonNullElse(open.peek(), NONE));
            return new ReadTokens(tokens, lines, firstPolicySet, innermost);
        }

        /**
         * @param index the index of a token, or the number of tokens for the end of
         * the document
         * @return the index of the innermost bracket that is open before it, or -1
         * if none is. Brackets do not stay open beyond the {@code ;} of their
         * statement.
         */
        int innermostOpenBracketBefore(int index) {
            return innermostOpenBracket.get(index);
        }
    }

    /**
     * What the parser would have accepted in place of the refused token. It
     * tells where in the grammar the parser was.
     *
     * @param types the types of the tokens the parser would have accepted
     */
    record Expected(IntervalSet types) {

        boolean contains(int type) {
            return types.contains(type);
        }

        boolean only(int... alternatives) {
            if (types.size() != alternatives.length) {
                return false;
            }
            for (val type : alternatives) {
                if (!types.contains(type)) {
                    return false;
                }
            }
            return true;
        }

        boolean nothingBut(int... alternatives) {
            return types.subtract(new IntervalSet(alternatives)).isNil();
        }

        /**
         * Where {@code permit}, {@code deny} or {@code suspend} must follow: the
         * effect of a policy, or the decision after {@code priority}.
         */
        boolean aDecision() {
            return only(SAPLLexer.PERMIT, SAPLLexer.DENY, SAPLLexer.SUSPEND);
        }

        /**
         * Where the voting mode of a policy set must follow.
         */
        boolean aVotingMode() {
            return only(SAPLLexer.FIRST, SAPLLexer.PRIORITY, SAPLLexer.UNANIMOUS, SAPLLexer.UNIQUE);
        }

        /**
         * Where a name can stand, but no value. A value can start with a name too,
         * but then a number is accepted as well.
         */
        boolean aName() {
            return contains(SAPLLexer.ID) && !contains(SAPLLexer.NUMBER);
        }

        /**
         * Where a value can start. Only there both a number and an object are
         * accepted.
         */
        boolean aValue() {
            return contains(SAPLLexer.NUMBER) && contains(SAPLLexer.LBRACE);
        }

        /**
         * Where a statement can start: a condition, which is a value, or a variable
         * definition.
         */
        boolean aStatement() {
            return aValue() && contains(SAPLLexer.VAR);
        }

        /**
         * Where a value must stand: after an operator, in a list, after the
         * {@code =} of a variable.
         */
        boolean aValueButNoStatement() {
            return aValue() && !contains(SAPLLexer.VAR);
        }

        /**
         * Before the policy or the policy set of the document: at its start and
         * after an import, where a further import can come first, and after a
         * schema statement, where only a further schema statement can. A schema
         * statement starts with the name of an element of the authorization
         * subscription.
         */
        boolean beforeThePolicy() {
            val policyCanStart       = contains(SAPLLexer.POLICY) && contains(SAPLLexer.SET);
            val importsCanFollow     = policyCanStart && contains(SAPLLexer.IMPORT);
            val onlySchemasCanFollow = policyCanStart && contains(SAPLLexer.SUBJECT)
                    && types.toList().stream().allMatch(type -> type == SAPLLexer.POLICY || type == SAPLLexer.SET
                            || Tokens.isSubscriptionElement(type));
            return importsCanFollow || onlySchemasCanFollow;
        }

        /**
         * After a voting mode, where {@code or} must follow. After
         * {@code unanimous} the parser also accepts {@code strict}.
         */
        boolean atEndOfVotingMode() {
            return only(SAPLLexer.KW_OR) || only(SAPLLexer.KW_OR, SAPLLexer.STRICT);
        }

        boolean atEndOfAttributeFinder() {
            return contains(SAPLLexer.GT)
                    && nothingBut(SAPLLexer.GT, SAPLLexer.LPAREN, SAPLLexer.LBRACKET, SAPLLexer.DOT);
        }

        /**
         * After the conditions of a policy, where only obligations, advice, a
         * transformation or the next policy can follow.
         */
        boolean followsTheConditions() {
            return nothingBut(SAPLLexer.OBLIGATION, SAPLLexer.ADVICE, SAPLLexer.TRANSFORM, SAPLLexer.POLICY);
        }
    }

    DocumentLines lines() {
        return read.lines();
    }

    boolean atObjectKey() {
        val startsAnEntry = previousType() == SAPLLexer.LBRACE || previousType() == SAPLLexer.COMMA;
        return startsAnEntry && expected.aName() && expected.contains(SAPLLexer.STRING)
                && !expected.contains(SAPLLexer.STAR);
    }

    /**
     * After {@code .} or {@code ..}, where a key stands.
     */
    boolean atKey() {
        return switch (previousType()) {
        case SAPLLexer.DOT    -> expected.contains(SAPLLexer.STAR) && expected.contains(SAPLLexer.STRING);
        case SAPLLexer.DOTDOT -> expected.contains(SAPLLexer.STAR);
        default               -> false;
        };
    }

    boolean atSubscript() {
        return previousType() == SAPLLexer.LBRACKET && expected.contains(SAPLLexer.QUESTION);
    }

    /**
     * After a complete value, where an operator, a step or the end of the
     * construct around the value can follow.
     * <p>
     * The parser accepts an operator after every value. Where the refused token
     * could follow a value at another place of the grammar, the parser has left
     * the value before it refuses, and tells only what ends the construct around
     * the value. A subscript and a filter block end with the same brackets as an
     * array and an object, but hold no values that an operator can follow.
     */
    boolean followsAValue() {
        val previous        = previous();
        val valueIsComplete = previous != null && Tokens.endsAnExpression(previous.getType());
        var constructCanEnd = false;
        for (val delimiter : DELIMITERS) {
            constructCanEnd |= expected.contains(delimiter);
        }
        val operatorCanFollow = expected.contains(SAPLLexer.AND) || (constructCanEnd && !inSubscriptOrFilterBlock());
        return valueIsComplete && operatorCanFollow;
    }

    /**
     * Whether the innermost bracket that is open at the refused token is the
     * bracket of a subscript or of a filter block. A {@code [} after a token
     * that can end a value is taken for a subscript.
     */
    private boolean inSubscriptOrFilterBlock() {
        val opening = innermostOpenBracket();
        if (opening <= 0) {
            return false;
        }
        val bracket = tokens().get(opening).getType();
        val before  = tokens().get(opening - 1).getType();
        return switch (bracket) {
        case SAPLLexer.LBRACE   -> before == SAPLLexer.FILTER;
        case SAPLLexer.LBRACKET -> Tokens.endsAnExpression(before) || before == SAPLLexer.DOTDOT;
        default                 -> false;
        };
    }

    /**
     * The index of the innermost bracket that is open at the refused token, or
     * -1 if none is.
     */
    private int innermostOpenBracket() {
        return read.innermostOpenBracketBefore(refusedIndex);
    }

    /**
     * Whether the token before the refused one belongs to the name of an
     * import.
     */
    boolean inImport() {
        var index = refusedIndex - 1;
        while (index >= 0 && Tokens.isPartOfPath(tokens().get(index).getType())) {
            index--;
        }
        return index >= 0 && tokens().get(index).getType() == SAPLLexer.IMPORT;
    }

    /**
     * Whether the refused token stands in a policy set. Only there a further
     * policy can start.
     */
    boolean inPolicySet() {
        return read.firstPolicySet() < refusedIndex;
    }

    /**
     * @return the token before the refused one, or null if there is none
     */
    @Nullable
    Token previous() {
        return refusedIndex > 0 ? tokens().get(refusedIndex - 1) : null;
    }

    /**
     * @return the type of the token before the refused one, or the end of the
     * document if there is none
     */
    int previousType() {
        val previous = previous();
        return previous == null ? Token.EOF : previous.getType();
    }

    boolean previousIsSubscriptionElement() {
        return Tokens.isSubscriptionElement(previousType());
    }

    /**
     * Before the policy or the policy set of the document, as
     * {@link Expected#beforeThePolicy()} tells it. After the value of a schema
     * statement the parser also accepts what continues the value, so there a
     * refused token that {@code schema} or {@code enforced} follows starts a
     * further schema statement.
     *
     * @return whether the refused token stands before the policy or the policy
     * set of the document
     */
    boolean beforeThePolicy() {
        val following              = following();
        val schemaStatementFollows = expected.contains(SAPLLexer.SUBJECT) && expected.contains(SAPLLexer.SET)
                && following != null
                && (following.getType() == SAPLLexer.SCHEMA || following.getType() == SAPLLexer.ENFORCED);
        return expected.beforeThePolicy() || schemaStatementFollows;
    }

    /**
     * @return the text of the token before the token before the refused one
     */
    String textBeforePrevious() {
        return refusedIndex > 1 ? tokens().get(refusedIndex - 2).getText() : "";
    }

    /**
     * @return the refused token, or null at the end of the document
     */
    @Nullable
    Token refused() {
        return refusedIndex < tokens().size() ? tokens().get(refusedIndex) : null;
    }

    /**
     * @return the type of the refused token, or the end of the document
     */
    int refusedType() {
        val refused = refused();
        return refused == null ? Token.EOF : refused.getType();
    }

    /**
     * @return the token after the refused one, or null if there is none
     */
    @Nullable
    Token following() {
        return afterRefused(1);
    }

    /**
     * @param distance how many tokens after the refused one
     * @return the token that far after the refused one, or null if there is none
     */
    @Nullable
    Token afterRefused(int distance) {
        val index = refusedIndex + distance;
        return index < tokens().size() ? tokens().get(index) : null;
    }

    /**
     * @return the region of the refused token, or the end of the document
     */
    Region refusedRegion() {
        val refused = refused();
        if (refused == null) {
            return Tokens.placeAt(lines().text().length(), lines());
        }
        return Tokens.regionOf(refused, lines());
    }

    /**
     * @return the region of a token
     */
    Region regionOf(Token token) {
        return Tokens.regionOf(token, lines());
    }

    /**
     * @return the region from the start of the token before the refused one to
     * the end of the refused one
     */
    Region previousAndRefused() {
        val refused  = refusedRegion();
        val previous = previous();
        val start    = previous == null ? refused.start() : Tokens.startOf(previous, lines());
        return Region.of(lines(), start, refused.end());
    }

    /**
     * @return the text of the document between two offsets
     */
    String text(int start, int end) {
        return Tokens.textOf(lines(), start, end);
    }

    /**
     * Whether the refused token stands on a later line than the token before,
     * or the document ends.
     */
    boolean onLaterLine() {
        val previous = previous();
        val refused  = refused();
        if (previous == null || refused == null) {
            return true;
        }
        val between = text(Tokens.endOf(previous, lines()), Tokens.startOf(refused, lines()));
        return between.indexOf('\n') >= 0 || between.indexOf('\r') >= 0;
    }

    /**
     * @return the path that ends at the token before the refused one, without
     * that token
     */
    String pathBeforePrevious() {
        val previous = previous();
        return previous == null ? "" : Tokens.pathBefore(tokens(), refusedIndex - 1, previous, lines());
    }

    /**
     * @return the path that starts with the refused token: names, keys and dots
     * that are written without space between them, or null if the refused token
     * cannot be part of a path
     */
    @Nullable
    String pathFromRefused() {
        val refused = refused();
        if (refused == null || !Tokens.isPartOfPath(refused.getType())) {
            return null;
        }
        var last = refusedIndex;
        while (last + 1 < tokens().size() && Tokens.isPartOfPath(tokens().get(last + 1).getType())
                && Tokens.adjacent(tokens().get(last), tokens().get(last + 1))) {
            last++;
        }
        return text(Tokens.startOf(refused, lines()), Tokens.endOf(tokens().get(last), lines()));
    }

    /**
     * @return the offset where the voting mode starts in the header of the
     * policy set the parser was in. It follows the keyword {@code set} and the
     * name of the policy set, or the keyword alone where the name is missing.
     */
    int votingModeStart() {
        val set = indexOfLastBefore(SAPLLexer.SET);
        if (set == NONE) {
            return refusedRegion().start();
        }
        val named             = set + 1 < refusedIndex && tokens().get(set + 1).getType() == SAPLLexer.STRING;
        val firstOfVotingMode = named ? set + 2 : set + 1;
        if (firstOfVotingMode < refusedIndex) {
            return Tokens.startOf(tokens().get(firstOfVotingMode), lines());
        }
        return refusedRegion().start();
    }

    /**
     * @return the offset after the last token inside the subscript that the
     * refused token stands in, or -1 if the subscript has no end or is closed
     * before the refused token
     */
    int endOfSubscript() {
        val opening = indexOfLastBefore(SAPLLexer.LBRACKET);
        if (opening == NONE) {
            return NONE;
        }
        var depth = 0;
        for (var index = opening; index < tokens().size(); index++) {
            val token = tokens().get(index);
            if (Bracket.openedBy(token) != null) {
                depth++;
            } else if (Bracket.closedBy(token) != null) {
                depth--;
            }
            if (depth <= 0) {
                val closedAtOrAfterRefused = depth == 0 && index >= refusedIndex;
                return closedAtOrAfterRefused ? Tokens.endOf(tokens().get(index - 1), lines()) : NONE;
            }
        }
        return NONE;
    }

    /**
     * The {@code <} or {@code |<} that opens the attribute finder the parser is
     * in. A {@code <} between the parentheses of its arguments or the brackets
     * of its options is a comparison.
     *
     * @return the token, or null if the parser is in no attribute finder
     */
    @Nullable
    Token openingOfAttributeFinder() {
        var bracketsToLeave = 0;
        for (var index = refusedIndex - 1; index >= 0; index--) {
            val token = tokens().get(index);
            if (Bracket.closedBy(token) != null) {
                bracketsToLeave++;
            } else if (Bracket.openedBy(token) != null) {
                if (bracketsToLeave == 0) {
                    return null;
                }
                bracketsToLeave--;
            } else if (bracketsToLeave == 0
                    && (token.getType() == SAPLLexer.LT || token.getType() == SAPLLexer.PIPE_LT)) {
                return token;
            }
        }
        return null;
    }

    /**
     * @return the last token of a type before the refused one, or null if there
     * is none
     */
    @Nullable
    Token lastBeforeRefused(int type) {
        val index = indexOfLastBefore(type);
        return index == NONE ? null : tokens().get(index);
    }

    /**
     * The variable definition around the refused token with one region
     * replaced. A definition reaches from its {@code var} to its {@code ;}.
     * Where the {@code ;} is missing, it ends before the next part of the
     * policy.
     */
    String variableDefinitionWith(Region replaced, String replacement) {
        val keyword = indexOfLastBefore(SAPLLexer.VAR);
        val start   = keyword == NONE ? replaced.start() : Tokens.startOf(tokens().get(keyword), lines());
        val end     = endOfVariableDefinition(replaced.end());
        return text(start, replaced.start()) + replacement + text(replaced.end(), end);
    }

    /**
     * @param earliest where the definition ends at the earliest
     * @return the end of the variable definition around the refused token: after
     * its {@code ;}, or before the next part of the policy where it is missing
     */
    private int endOfVariableDefinition(int earliest) {
        var end = earliest;
        for (var index = refusedIndex; index < tokens().size(); index++) {
            val part = tokens().get(index);
            if (Tokens.startsAPolicyPart(part.getType())) {
                return end;
            }
            end = Tokens.endOf(part, lines());
            if (part.getType() == SAPLLexer.SEMI) {
                return end;
            }
        }
        return end;
    }

    /**
     * @return the index of the last token of a type before the refused one, or
     * -1 if there is none
     */
    private int indexOfLastBefore(int type) {
        var index = refusedIndex - 1;
        while (index >= 0 && tokens().get(index).getType() != type) {
            index--;
        }
        return index;
    }

    private List<? extends Token> tokens() {
        return read.tokens();
    }

}
