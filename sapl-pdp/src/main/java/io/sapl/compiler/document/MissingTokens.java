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
import io.sapl.compiler.diagnostics.FurtherRegion;
import io.sapl.compiler.diagnostics.FurtherRegion.Label;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The mistakes where something is missing before the refused token: a name, a
 * value, a {@code ;}. The last token before the gap is marked, because that is
 * where the author continues writing.
 */
@UtilityClass
class MissingTokens {

    private static final int[] DEFAULT_DECISIONS = { SAPLLexer.PERMIT, SAPLLexer.DENY, SAPLLexer.SUSPEND,
            SAPLLexer.ABSTAIN };

    /**
     * What stands for the function of a filter statement where none follows its
     * target.
     */
    private static final String SOME_FUNCTION = "<function>";

    /**
     * @param refusal where the parser refused a token
     * @return what is missing before the refused token, if it is a known mistake
     */
    static Optional<Diagnostic> before(Refusal refusal) {
        if (refusal.beforeThePolicy()) {
            return Optional.of(Found.of(Problem.EMPTY_DOCUMENT).atRefused(refusal));
        }
        val previous = refusal.previous();
        if (previous == null) {
            return Optional.empty();
        }
        val found = missingInHeader(refusal, previous).or(() -> missingInImportOrSchema(refusal, previous))
                .or(() -> missingInDefinition(refusal, previous)).or(() -> missingInStep(refusal, previous))
                .or(() -> missingInFilter(refusal, previous)).or(() -> missingValue(refusal, previous));
        if (found.isPresent()) {
            return found.map(problem -> problem.at(refusal.regionOf(previous)));
        }
        return incomplete(refusal, previous);
    }

    /**
     * What is missing in the header of a policy or a policy set.
     */
    private static Optional<Found> missingInHeader(Refusal refusal, Token previous) {
        val type = previous.getType();
        if ((type == SAPLLexer.POLICY || type == SAPLLexer.SET) && refusal.expected().only(SAPLLexer.STRING)) {
            return Optional.of(
                    Found.of(type == SAPLLexer.SET ? Problem.MISSING_POLICY_SET_NAME : Problem.MISSING_POLICY_NAME));
        }
        if (type == SAPLLexer.PRIORITY && refusal.expected().aDecision()) {
            return Optional.of(Found.of(Problem.INCOMPLETE_VOTING_MODE));
        }
        if (refusal.expected().aDecision()) {
            return Optional.of(Found.of(Problem.MISSING_EFFECT));
        }
        if (refusal.expected().aVotingMode()) {
            return Optional.of(Found.of(Problem.MISSING_ALGORITHM, previous.getText()));
        }
        if (refusal.expected().atEndOfVotingMode()) {
            val votingMode = refusal.text(refusal.votingModeStart(), Tokens.endOf(previous, refusal.lines()));
            return Optional.of(Found.of(Problem.MISSING_OR, votingMode));
        }
        if (refusal.expected().only(DEFAULT_DECISIONS)) {
            return Optional.of(Found.of(Problem.MISSING_DEFAULT_DECISION));
        }
        if (refusal.refused() == null && refusal.expected().contains(SAPLLexer.POLICY)
                && !refusal.expected().aValue()) {
            return Optional.of(Found.of(Problem.EMPTY_POLICY_SET));
        }
        return Optional.empty();
    }

    /**
     * What is missing in an import or a schema statement.
     */
    private static Optional<Found> missingInImportOrSchema(Refusal refusal, Token previous) {
        val type = previous.getType();
        if (refusal.inImport() && (refusal.expected().aName() || refusal.expected().only(SAPLLexer.DOT))) {
            return Optional.of(Found.of(Problem.INCOMPLETE_IMPORT));
        }
        if (type == SAPLLexer.AS && refusal.expected().aName()) {
            return Optional.of(Found.of(Problem.MISSING_ALIAS));
        }
        if (refusal.expected().only(SAPLLexer.SCHEMA, SAPLLexer.ENFORCED)) {
            return Optional.of(Found.of(Problem.MISSING_SCHEMA_KEYWORD, previous.getText()));
        }
        if (type == SAPLLexer.ENFORCED && refusal.expected().only(SAPLLexer.SCHEMA)) {
            return Optional.of(Found.of(Problem.MISSING_SCHEMA_KEYWORD_AFTER_ENFORCED, refusal.textBeforePrevious()));
        }
        return Optional.empty();
    }

    /**
     * What is missing in a variable definition or an object entry.
     */
    private static Optional<Found> missingInDefinition(Refusal refusal, Token previous) {
        if (previous.getType() == SAPLLexer.VAR && refusal.expected().aName()) {
            return Optional.of(Found.of(Problem.MISSING_VARIABLE_NAME));
        }
        if (refusal.expected().only(SAPLLexer.ASSIGN)) {
            return Optional.of(Found.of(Problem.VARIABLE_WITHOUT_VALUE, previous.getText()));
        }
        if (refusal.expected().only(SAPLLexer.COLON)) {
            return Optional.of(Found.of(Problem.MISSING_COLON, previous.getText()));
        }
        return Optional.empty();
    }

    /**
     * What is missing in a key, a subscript or an attribute finder.
     */
    private static Optional<Found> missingInStep(Refusal refusal, Token previous) {
        val type = previous.getType();
        if ((type == SAPLLexer.DOT || type == SAPLLexer.DOTDOT) && refusal.atKey()) {
            val problem = type == SAPLLexer.DOTDOT ? Problem.MISSING_KEY_AFTER_RECURSIVE_DESCENT : Problem.MISSING_KEY;
            return Optional.of(Found.of(problem, refusal.pathBeforePrevious()));
        }
        if (type == SAPLLexer.QUESTION && refusal.expected().only(SAPLLexer.LPAREN)) {
            val end = refusal.endOfSubscript();
            if (end < 0) {
                return Optional.empty();
            }
            val condition = refusal.text(Tokens.endOf(previous, refusal.lines()), end).trim();
            return Optional.of(Found.of(Problem.CONDITION_WITHOUT_PARENTHESES, condition));
        }
        if ((type == SAPLLexer.LT || type == SAPLLexer.PIPE_LT) && refusal.expected().aName()) {
            return Optional.of(Found.of(Problem.MISSING_ATTRIBUTE_FINDER_NAME));
        }
        return Optional.empty();
    }

    /**
     * What is missing in a filter, or in the name of a function or attribute
     * finder.
     */
    private static Optional<Found> missingInFilter(Refusal refusal, Token previous) {
        val type           = previous.getType();
        val nameIsExpected = refusal.expected().aName();
        if (refusal.expected().only(SAPLLexer.EACH, SAPLLexer.AT)) {
            return Optional.of(Found.of(Problem.MISSING_FILTER_TARGET));
        }
        if (type == SAPLLexer.FILTER && refusal.expected().contains(SAPLLexer.EACH)) {
            return Optional.of(Found.of(Problem.MISSING_FILTER_FUNCTION));
        }
        if (type == SAPLLexer.EACH && nameIsExpected) {
            return Optional.of(Found.of(Problem.MISSING_FILTER_FUNCTION_AFTER_EACH));
        }
        if (type == SAPLLexer.EACH && refusal.expected().only(SAPLLexer.AT)) {
            return Optional.of(Found.of(Problem.MISSING_FILTER_TARGET_AFTER_EACH));
        }
        if (type == SAPLLexer.COLON && nameIsExpected) {
            return Optional.of(Found.of(Problem.FILTER_STATEMENT_WITHOUT_FUNCTION));
        }
        if (type == SAPLLexer.DOT && nameIsExpected) {
            return Optional.of(Found.of(Problem.INCOMPLETE_NAME));
        }
        if (refusal.expected().only(SAPLLexer.DOT, SAPLLexer.DOTDOT, SAPLLexer.COLON, SAPLLexer.LBRACKET)) {
            return missingFilterColon(refusal, previous);
        }
        return Optional.empty();
    }

    /**
     * A filter statement in which the function follows the target without a
     * {@code :}.
     */
    private static Optional<Found> missingFilterColon(Refusal refusal, Token previous) {
        val target = refusal.lastBeforeRefused(SAPLLexer.AT);
        if (target == null) {
            return Optional.empty();
        }
        val lines    = refusal.lines();
        val written  = refusal.text(Tokens.startOf(target, lines), Tokens.endOf(previous, lines));
        val function = refusal.pathFromRefused();
        return Optional
                .of(Found.of(Problem.MISSING_FILTER_COLON, written, function == null ? SOME_FUNCTION : function));
    }

    private static Optional<Found> missingValue(Refusal refusal, Token previous) {
        if (refusal.expected().aValueButNoStatement()) {
            return Optional.of(Found.of(Problem.MISSING_VALUE, previous.getText()));
        }
        return Optional.empty();
    }

    /**
     * What is missing where one token fills the gap. That token is offered for
     * insertion. Between two values the missing token is not certain: an
     * operator is missing there.
     */
    private static Optional<Diagnostic> incomplete(Refusal refusal, Token previous) {
        val after = refusal.regionOf(previous);
        if (refusal.expected().only(SAPLLexer.IN)) {
            return Optional.of(Found.of(Problem.MISSING_IN, previous.getText()).insertingAfter(refusal, after, " in"));
        }
        if (refusal.expected().atEndOfAttributeFinder()) {
            return unclosedAttributeFinder(refusal, previous);
        }
        val refused = refusal.refused();
        if (refused != null && refused.getType() == SAPLLexer.STRING && refusal.expected().contains(SAPLLexer.POLICY)
                && refusal.inPolicySet()) {
            val found = Found.of(Problem.MISSING_POLICY_KEYWORD, refused.getText());
            return Optional.of(found.insertingAfter(refusal, after, " policy"));
        }
        val refusedType        = refusal.refusedType();
        val somethingNewStarts = refused == null || Tokens.startsAValue(refusedType)
                || Tokens.startsAPolicyPart(refusedType);
        if (!(refusal.followsAValue() && somethingNewStarts)) {
            return Optional.empty();
        }
        val list = missingCommaIn(refusal);
        if (list != null) {
            return Optional.of(Found.of(list).insertingAfter(refusal, after, ","));
        }
        if (!refusal.expected().contains(SAPLLexer.SEMI)) {
            return Optional.empty();
        }
        if (refusal.onLaterLine() || Tokens.startsAPolicyPart(refusedType)) {
            return Optional.of(Found.of(Problem.MISSING_SEMICOLON).insertingAfter(refusal, after, ";"));
        }
        return Optional.of(Found.of(Problem.MISSING_OPERATOR).at(after));
    }

    private static Optional<Diagnostic> unclosedAttributeFinder(Refusal refusal, Token previous) {
        val opening = refusal.openingOfAttributeFinder();
        if (opening == null) {
            return Optional.empty();
        }
        val lines    = refusal.lines();
        val name     = refusal.text(Tokens.endOf(opening, lines), Tokens.endOf(previous, lines));
        val region   = refusal.regionOf(previous);
        val quickFix = new QuickFix(Tokens.placeAt(region.end(), lines), ">");
        return Optional.of(new Diagnostic(Problem.UNCLOSED_ATTRIBUTE_FINDER, List.of(name), region,
                List.of(new FurtherRegion(refusal.regionOf(opening), Label.OPENED_HERE)), quickFix));
    }

    /**
     * The problem of the missing comma in the list the parser is in, if it
     * accepts a {@code ,} here.
     */
    private static @Nullable Problem missingCommaIn(Refusal refusal) {
        if (!refusal.expected().contains(SAPLLexer.COMMA)) {
            return null;
        }
        if (refusal.expected().contains(SAPLLexer.RBRACKET)) {
            return Problem.MISSING_COMMA_IN_ARRAY;
        }
        if (refusal.expected().contains(SAPLLexer.RPAREN)) {
            return Problem.MISSING_COMMA_IN_ARGUMENTS;
        }
        if (refusal.expected().contains(SAPLLexer.RBRACE)) {
            return Problem.MISSING_COMMA_IN_OBJECT;
        }
        return null;
    }

}
