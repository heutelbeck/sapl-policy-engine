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

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The mistakes where the refused token is wrong: an operator of another
 * language, a keyword used as a name, a part of a policy in the wrong place. The
 * token is marked, and where the correct form is certain, it is offered as the
 * replacement.
 */
@UtilityClass
class WrongTokens {

    /**
     * A name of a combining algorithm in SAPL 3 and how it is written in SAPL 4.
     */
    private record LegacyAlgorithm(String name, String replacement) {}

    private static final List<LegacyAlgorithm> LEGACY_ALGORITHMS = List.of(
            new LegacyAlgorithm("deny-overrides", "priority deny or abstain errors propagate"),
            new LegacyAlgorithm("permit-overrides", "priority permit or abstain errors propagate"),
            new LegacyAlgorithm("permit-unless-deny", "priority deny or permit"),
            new LegacyAlgorithm("deny-unless-permit", "priority permit or deny"),
            new LegacyAlgorithm("first-applicable", "first or abstain errors propagate"),
            new LegacyAlgorithm("only-one-applicable", "unique or abstain errors propagate"));

    private static final List<String> DECISION_KEYWORDS = List.of("permit", "deny", "suspend");

    /**
     * A word is taken for a misspelled keyword if at most this many of its
     * characters have to be changed, added or removed to get the keyword. A
     * shorter word than the minimum is not: that many changes replace it
     * entirely.
     */
    private static final int MAX_MISSPELLING_DISTANCE   = 2;
    private static final int MIN_MISSPELLED_WORD_LENGTH = 3;

    /**
     * What the refused token is, if it is a mistake that is known. The places of
     * a document are asked in a fixed order: where two of them know the refused
     * token, the earlier one tells what it is.
     *
     * @param refusal where the parser refused a token
     * @return the problem of the refused token, if it is a known mistake
     */
    static Optional<Diagnostic> of(Refusal refusal) {
        val refused = refusal.refused();
        if (refused == null) {
            return Optional.empty();
        }
        if (refusal.beforeThePolicy()) {
            return Optional.of(Found.of(Problem.MISSING_POLICY_HEADER).atRefused(refusal));
        }
        return ChainedComparison.of(refusal, refused).or(() -> wrongInHeader(refusal, refused))
                .or(() -> wrongInVariableDefinition(refusal, refused)).or(() -> wrongInObject(refusal, refused))
                .or(() -> wrongInStep(refusal, refused)).or(() -> wrongAfterConditions(refusal, refused))
                .or(() -> wrongAtStartOfStatement(refusal, refused)).or(() -> operatorReadAsTwoTokens(refusal, refused))
                .or(() -> wrongInPlaceOfValue(refusal, refused)).or(() -> wrongAfterValue(refusal, refused));
    }

    /**
     * A wrong token in the header of a policy or a policy set.
     */
    private static Optional<Diagnostic> wrongInHeader(Refusal refusal, Token refused) {
        val previous = refusal.previousType();
        val type     = refused.getType();
        val text     = refused.getText();
        if (type == SAPLLexer.ID && refusal.expected().only(SAPLLexer.STRING)
                && (previous == SAPLLexer.POLICY || previous == SAPLLexer.SET)) {
            val problem = previous == SAPLLexer.SET ? Problem.UNQUOTED_POLICY_SET_NAME : Problem.UNQUOTED_POLICY_NAME;
            return Optional.of(Found.of(problem, text).replacingRefused(refusal, quoted(text)));
        }
        if (type == SAPLLexer.ID && refusal.expected().aDecision()) {
            return misspelledDecision(refusal, text, previous == SAPLLexer.PRIORITY);
        }
        if (type == SAPLLexer.IMPORT && refusal.expected().aDecision() && previous != SAPLLexer.PRIORITY) {
            return Optional.of(Found.of(Problem.MISPLACED_IMPORT).atRefused(refusal));
        }
        return wrongInAlgorithmOrImport(refusal, refused);
    }

    /**
     * A wrong token in the combining algorithm of a policy set, or in the name
     * of an import.
     */
    private static Optional<Diagnostic> wrongInAlgorithmOrImport(Refusal refusal, Token refused) {
        val type = refused.getType();
        if (refusal.expected().aVotingMode()) {
            return legacyAlgorithm(refusal, refusal.refusedRegion().start());
        }
        if (refusal.expected().atEndOfVotingMode()) {
            return legacyAlgorithm(refusal, refusal.votingModeStart());
        }
        if (type == SAPLLexer.ID && refusal.expected().only(SAPLLexer.ABSTAIN, SAPLLexer.PROPAGATE)) {
            return Optional.of(Found.of(Problem.UNKNOWN_ERROR_HANDLING, refused.getText()).atRefused(refusal));
        }
        if (type == SAPLLexer.STAR && refusal.previousType() == SAPLLexer.DOT && refusal.expected().aName()
                && refusal.inImport()) {
            return Optional.of(Found.of(Problem.LIBRARY_IMPORT).atRefused(refusal));
        }
        return Optional.empty();
    }

    private static Optional<Diagnostic> misspelledDecision(Refusal refusal, String word, boolean afterPriority) {
        val suggestion = closest(word, DECISION_KEYWORDS);
        if (suggestion == null) {
            return Optional.empty();
        }
        val problem = afterPriority ? Problem.UNKNOWN_PRIORITY : Problem.UNKNOWN_EFFECT;
        return Optional.of(Found.of(problem, word, suggestion).replacingRefused(refusal, suggestion));
    }

    /**
     * A name of a combining algorithm of SAPL 3 that is written at an offset.
     */
    private static Optional<Diagnostic> legacyAlgorithm(Refusal refusal, int start) {
        val text = refusal.lines().text();
        for (val algorithm : LEGACY_ALGORITHMS) {
            if (text.startsWith(algorithm.name(), start)) {
                val replacement = algorithm.replacement();
                val region      = Region.of(refusal.lines(), start, start + algorithm.name().length());
                return Optional.of(Found.of(Problem.LEGACY_ALGORITHM, replacement).replacing(region, replacement));
            }
        }
        return Optional.empty();
    }

    /**
     * A wrong token where the name or the {@code =} of a variable definition
     * stands.
     */
    private static Optional<Diagnostic> wrongInVariableDefinition(Refusal refusal, Token refused) {
        val type           = refused.getType();
        val text           = refused.getText();
        val nameIsExpected = refusal.previousType() == SAPLLexer.VAR && refusal.expected().aName();
        if (type == SAPLLexer.STRING && nameIsExpected) {
            val name      = withoutQuotationMarks(text);
            val corrected = refusal.variableDefinitionWith(refusal.refusedRegion(), name);
            return Optional.of(Found.of(Problem.QUOTED_VARIABLE_NAME, corrected).replacingRefused(refusal, name));
        }
        if (Tokens.isKeyword(type) && nameIsExpected) {
            return Optional.of(Found.of(Problem.KEYWORD_AS_NAME, text).replacingRefused(refusal, backticked(text)));
        }
        // The grammar does not take the name of an element of the authorization
        // subscription for the name of a variable.
        if (nameIsExpected && Tokens.isSubscriptionElement(type)) {
            return Optional.of(Found.of(Problem.RESERVED_VARIABLE_NAME, text).atRefused(refusal));
        }
        if (type == SAPLLexer.EQ && refusal.expected().only(SAPLLexer.ASSIGN)) {
            val corrected = refusal.variableDefinitionWith(refusal.refusedRegion(), "=");
            return Optional.of(Found.of(Problem.VARIABLE_WITH_COMPARISON, corrected).replacingRefused(refusal, "="));
        }
        return Optional.empty();
    }

    /**
     * A wrong token in an entry of an object.
     */
    private static Optional<Diagnostic> wrongInObject(Refusal refusal, Token refused) {
        val type = refused.getType();
        if (type == SAPLLexer.ASSIGN && refusal.expected().only(SAPLLexer.COLON)) {
            return Optional.of(Found.of(Problem.WRONG_KEY_SEPARATOR).replacingRefused(refusal, ":"));
        }
        if (Tokens.isKeyword(type) && refusal.atObjectKey()) {
            val keyword = refused.getText();
            return Optional
                    .of(Found.of(Problem.KEYWORD_AS_OBJECT_KEY, keyword).replacingRefused(refusal, quoted(keyword)));
        }
        if (refusal.atObjectKey()) {
            return Optional.of(Found.of(Problem.INVALID_OBJECT_KEY).atRefused(refusal));
        }
        return Optional.empty();
    }

    /**
     * A wrong token where a key, a subscript or a filter block stands.
     */
    private static Optional<Diagnostic> wrongInStep(Refusal refusal, Token refused) {
        val type     = refused.getType();
        val previous = refusal.previousType();
        if (Tokens.isKeyword(type) && refusal.atKey()) {
            val keyword = refused.getText();
            val found   = Found.of(Problem.KEYWORD_AS_KEY, keyword, refusal.pathBeforePrevious());
            return Optional.of(found.replacingRefused(refusal, backticked(keyword)));
        }
        if (refusal.atSubscript()) {
            return wrongInSubscript(refusal, refused);
        }
        val stringInUnionOfIndices = type == SAPLLexer.STRING
                && refusal.expected().only(SAPLLexer.MINUS, SAPLLexer.NUMBER);
        val indexInUnionOfKeys     = type == SAPLLexer.NUMBER && previous == SAPLLexer.COMMA
                && refusal.expected().only(SAPLLexer.STRING);
        if (stringInUnionOfIndices || indexInUnionOfKeys) {
            return Optional.of(Found.of(Problem.MIXED_UNION).atRefused(refusal));
        }
        if (type == SAPLLexer.COLON && refusal.expected().only(SAPLLexer.RBRACKET)) {
            return Optional.of(Found.of(Problem.TOO_MANY_SLICE_PARTS).atRefused(refusal));
        }
        if (type == SAPLLexer.RBRACE && previous == SAPLLexer.LBRACE
                && refusal.expected().only(SAPLLexer.EACH, SAPLLexer.AT)) {
            return Optional.of(Found.of(Problem.EMPTY_FILTER_BLOCK).at(refusal.previousAndRefused()));
        }
        return Optional.empty();
    }

    private static Optional<Diagnostic> wrongInSubscript(Refusal refusal, Token refused) {
        val type = refused.getType();
        if (type == SAPLLexer.RBRACKET) {
            return Optional.of(Found.of(Problem.EMPTY_SUBSCRIPT).at(refusal.previousAndRefused()));
        }
        if (Tokens.startsAValue(type)) {
            val end = refusal.endOfSubscript();
            if (end < 0) {
                return Optional.empty();
            }
            val inside     = Region.of(refusal.lines(), refusal.refusedRegion().start(), end);
            val expression = refusal.text(inside.start(), inside.end());
            val found      = Found.of(Problem.EXPRESSION_IN_SUBSCRIPT, expression);
            return Optional.of(found.replacing(inside, "(" + expression + ")"));
        }
        return Optional.of(Found.of(Problem.INVALID_SUBSCRIPT).atRefused(refusal));
    }

    /**
     * A wrong token after the conditions of a policy, where obligations, then
     * advice, then one transformation follow.
     */
    private static Optional<Diagnostic> wrongAfterConditions(Refusal refusal, Token refused) {
        if (!refusal.expected().followsTheConditions()) {
            return Optional.empty();
        }
        val type = refused.getType();
        return switch (type) {
        case SAPLLexer.SEMI                         ->
            Optional.of(Found.of(Problem.SEMICOLON_AFTER_PART).replacingRefused(refusal, ""));
        case SAPLLexer.OBLIGATION, SAPLLexer.ADVICE -> {
            val afterTransformation = !refusal.expected().contains(SAPLLexer.ADVICE)
                    && !refusal.expected().contains(SAPLLexer.TRANSFORM);
            val problem             = afterTransformation ? Problem.MISPLACED_PART_AFTER_TRANSFORMATION
                    : Problem.MISPLACED_PART_AFTER_ADVICE;
            yield Optional.of(Found.of(problem, refused.getText()).atRefused(refusal));
        }
        case SAPLLexer.TRANSFORM                    ->
            Optional.of(Found.of(Problem.SECOND_TRANSFORMATION).atRefused(refusal));
        case SAPLLexer.POLICY                       -> Optional.of(Found.of(Problem.SECOND_POLICY).atRefused(refusal));
        default                                     -> {
            if (type == SAPLLexer.VAR || Tokens.startsAValue(type)) {
                yield Optional.of(Found.of(Problem.MISPLACED_STATEMENT).atRefused(refusal));
            }
            yield Optional.empty();
        }
        };
    }

    /**
     * A wrong token where a statement starts.
     */
    private static Optional<Diagnostic> wrongAtStartOfStatement(Refusal refusal, Token refused) {
        if (!refusal.expected().aStatement()) {
            return Optional.empty();
        }
        return switch (refused.getType()) {
        case SAPLLexer.SEMI   -> Optional.of(Found.of(Problem.SUPERFLUOUS_SEMICOLON).replacingRefused(refusal, ""));
        case SAPLLexer.SET    -> refusal.expected().contains(SAPLLexer.POLICY)
                ? Optional.of(Found.of(Problem.NESTED_POLICY_SET).atRefused(refusal))
                : Optional.empty();
        case SAPLLexer.IMPORT -> Optional.of(Found.of(Problem.MISPLACED_IMPORT).atRefused(refusal));
        default               -> Optional.empty();
        };
    }

    /**
     * An operator of another language that was read as two tokens: an operator
     * of SAPL directly followed by a character that does not belong to it, as in
     * {@code ===}. The parser refuses the second token where a value must stand,
     * and the first one after a comparison.
     */
    private static Optional<Diagnostic> operatorReadAsTwoTokens(Refusal refusal, Token refused) {
        Token first;
        Token second;
        if (refusal.expected().aValueButNoStatement()) {
            first  = refusal.previous();
            second = refused;
        } else if (refusal.followsAValue()) {
            first  = refused;
            second = refusal.following();
        } else {
            return Optional.empty();
        }
        if (first == null || second == null || !Tokens.adjacent(first, second)) {
            return Optional.empty();
        }
        val region = Tokens.regionOf(first, second, refusal.lines());
        val types  = List.of(first.getType(), second.getType());
        if (types.equals(List.of(SAPLLexer.EQ, SAPLLexer.ASSIGN))) {
            return Optional.of(unknownOperator("===", "==", region));
        }
        if (types.equals(List.of(SAPLLexer.NEQ, SAPLLexer.ASSIGN))) {
            return Optional.of(unknownOperator("!==", "!=", region));
        }
        if (types.equals(List.of(SAPLLexer.LT, SAPLLexer.GT))) {
            return Optional.of(unknownOperator("<>", "!=", region));
        }
        return Optional.empty();
    }

    /**
     * A wrong token where a value must stand.
     */
    private static Optional<Diagnostic> wrongInPlaceOfValue(Refusal refusal, Token refused) {
        val previous = refusal.previous();
        if (!refusal.expected().aValueButNoStatement() || previous == null) {
            return Optional.empty();
        }
        val type    = refused.getType();
        val closing = type == SAPLLexer.RPAREN || type == SAPLLexer.RBRACKET || type == SAPLLexer.RBRACE;
        if (closing && previous.getType() == SAPLLexer.COMMA) {
            return Optional.of(Found.of(Problem.TRAILING_COMMA).replacing(refusal.regionOf(previous), ""));
        }
        if (type == SAPLLexer.RPAREN && previous.getType() == SAPLLexer.LPAREN) {
            return Optional.of(Found.of(Problem.EMPTY_PARENTHESES).at(refusal.previousAndRefused()));
        }
        return Optional.empty();
    }

    /**
     * A wrong token after a complete value.
     */
    private static Optional<Diagnostic> wrongAfterValue(Refusal refusal, Token refused) {
        if (!refusal.followsAValue()) {
            return Optional.empty();
        }
        val type = refused.getType();
        if (type == SAPLLexer.ASSIGN) {
            return Optional.of(Found.of(Problem.ASSIGNMENT_AS_COMPARISON).replacingRefused(refusal, "=="));
        }
        if (type == SAPLLexer.KW_OR) {
            return Optional.of(unknownOperator("or", "||", refusal.refusedRegion()));
        }
        if (isWord(refused, "and")) {
            return Optional.of(unknownOperator("and", "&&", refusal.refusedRegion()));
        }
        if (isWord(refused, "where")) {
            return Optional.of(Found.of(Problem.WHERE_KEYWORD).replacingRefused(refusal, ";"));
        }
        if ((type == SAPLLexer.SCHEMA || type == SAPLLexer.ENFORCED) && refusal.previousIsSubscriptionElement()) {
            return Optional.of(Found.of(Problem.MISPLACED_SCHEMA).atRefused(refusal));
        }
        val previous = refusal.previous();
        if (previous != null && isWord(previous, "not") && Tokens.startsAValue(type)) {
            // The replacement takes the space after 'not' with it: 'not a' becomes '!a'.
            val not = refusal.regionOf(previous);
            return Optional.of(unknownOperator("not", "!", not,
                    Region.of(refusal.lines(), not.start(), refusal.refusedRegion().start())));
        }
        return Optional.empty();
    }

    private static Diagnostic unknownOperator(String found, String replacement, Region region) {
        return unknownOperator(found, replacement, region, region);
    }

    /**
     * @param marked the region of the operator that is marked
     * @param replaced the region that the operator of SAPL replaces
     */
    private static Diagnostic unknownOperator(String found, String replacement, Region marked, Region replaced) {
        return Found.of(Problem.UNKNOWN_OPERATOR, found, replacement).fixedBy(marked,
                new QuickFix(replaced, replacement));
    }

    private static boolean isWord(Token token, String word) {
        return token.getType() == SAPLLexer.ID && word.equals(token.getText());
    }

    private static String quoted(String text) {
        return "\"" + text + "\"";
    }

    private static String backticked(String text) {
        return "`" + text + "`";
    }

    private static String withoutQuotationMarks(String text) {
        var start = 0;
        var end   = text.length();
        while (start < end && isQuotationMark(text.charAt(start))) {
            start++;
        }
        while (end > start && isQuotationMark(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isQuotationMark(char character) {
        return character == '"' || character == '\'';
    }

    /**
     * The keyword that a word was most likely meant to be: the only one it is a
     * misspelling of.
     */
    private static @Nullable String closest(String word, List<String> keywords) {
        if (word.codePointCount(0, word.length()) < MIN_MISSPELLED_WORD_LENGTH) {
            return null;
        }
        val near = keywords.stream().filter(keyword -> editDistance(word, keyword) <= MAX_MISSPELLING_DISTANCE)
                .toList();
        return near.size() == 1 ? near.getFirst() : null;
    }

    /**
     * The number of characters to change, add or remove to turn one word into
     * the other.
     */
    private static int editDistance(String from, String to) {
        val target    = to.codePoints().toArray();
        var distances = new int[target.length + 1];
        for (var index = 0; index <= target.length; index++) {
            distances[index] = index;
        }
        var read = 0;
        for (val character : from.codePoints().toArray()) {
            read++;
            val next = new int[target.length + 1];
            next[0] = read;
            for (var index = 1; index <= target.length; index++) {
                val byChanging = distances[index - 1] + (character == target[index - 1] ? 0 : 1);
                next[index] = Math.min(byChanging, Math.min(next[index - 1], distances[index]) + 1);
            }
            distances = next;
        }
        return distances[target.length];
    }

}
