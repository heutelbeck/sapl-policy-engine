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
import io.sapl.compiler.document.Tokens.Bracket;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.ComparisonContext;
import io.sapl.grammar.antlr.SAPLParser.EqualityContext;
import io.sapl.grammar.antlr.SAPLParser.HasExpressionContext;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A comparison applied to the result of a comparison, as in {@code a < b < c}.
 * A comparison has two operands: the second operator would compare the result
 * of the first comparison. The grammar does not read such a chain, so the
 * parser refuses the second operator, and the comparison before it tells what
 * the author wrote.
 */
@UtilityClass
class ChainedComparison {

    // How strongly the binary operators bind, from the weakest.
    private static final int LAZY_OR      = 0;
    private static final int LAZY_AND     = 1;
    private static final int EAGER_OR     = 2;
    private static final int EXCLUSIVE_OR = 3;
    private static final int EAGER_AND    = 4;
    private static final int EQUALITY     = 5;
    private static final int HAS          = 6;
    private static final int COMPARISON   = 7;

    /**
     * A comparison has two operands and its operator, which can be two words, as
     * in {@code has any}.
     */
    private static final int FEWEST_PARTS_OF_A_COMPARISON = 3;

    /**
     * The binary operators by how strongly they bind. The operators of equality,
     * of {@code has} and of comparison cannot be chained.
     */
    private static final Map<Integer, Integer> PRECEDENCE = Map.ofEntries(Map.entry(SAPLLexer.OR, LAZY_OR),
            Map.entry(SAPLLexer.AND, LAZY_AND), Map.entry(SAPLLexer.BITOR, EAGER_OR),
            Map.entry(SAPLLexer.BITXOR, EXCLUSIVE_OR), Map.entry(SAPLLexer.BITAND, EAGER_AND),
            Map.entry(SAPLLexer.EQ, EQUALITY), Map.entry(SAPLLexer.NEQ, EQUALITY), Map.entry(SAPLLexer.REGEX, EQUALITY),
            Map.entry(SAPLLexer.HAS, HAS), Map.entry(SAPLLexer.LT, COMPARISON), Map.entry(SAPLLexer.LE, COMPARISON),
            Map.entry(SAPLLexer.GT, COMPARISON), Map.entry(SAPLLexer.GE, COMPARISON),
            Map.entry(SAPLLexer.IN, COMPARISON), Map.entry(SAPLLexer.ANY, COMPARISON),
            Map.entry(SAPLLexer.ALL, COMPARISON));

    /**
     * @param refusal where the parser refused a token
     * @param refused the refused token
     * @return the chained comparison, if the refused token is the operator of a
     * comparison that follows a comparison of the same kind
     */
    static Optional<Diagnostic> of(Refusal refusal, Token refused) {
        val level = PRECEDENCE.get(refused.getType());
        if (level == null || level < EQUALITY || !refusal.followsAValue()) {
            return Optional.empty();
        }
        val first = comparisonBefore(refusal.context(), level, refusal.previous());
        val right = operandAfter(refusal, level);
        if (first == null || right == null) {
            return Optional.empty();
        }
        val left         = textOf(refusal, first.getChild(0));
        val operator     = textBetween(refusal, first.getChild(1), first.getChild(first.getChildCount() - 2));
        val middle       = textOf(refusal, first.getChild(first.getChildCount() - 1));
        val second       = refused.getText();
        val testEquality = level == EQUALITY;
        if (testEquality) {
            val rewritten = "(" + left + " " + operator + " " + middle + ") " + second + " " + right;
            return Optional.of(Found.of(Problem.CHAINED_COMPARISON_OF_EQUALITY, rewritten).atRefused(refusal));
        }
        val rewritten = left + " " + operator + " " + middle + " && " + middle + " " + second + " " + right;
        return Optional.of(Found.of(Problem.CHAINED_COMPARISON, rewritten).atRefused(refusal));
    }

    /**
     * The comparison of a kind that the parser completed last in the rules
     * around the place where it refused the token. A comparison that the parser
     * read only in part, after it refused an earlier token of the statement, is
     * none: it can hold a token that the parser added or an operand without a
     * token, and neither has a text to quote.
     */
    private static @Nullable ParserRuleContext comparisonBefore(@Nullable ParserRuleContext context, int level,
            @Nullable Token previous) {
        for (var rule = context; rule != null; rule = rule.getParent()) {
            if (levelOf(rule) == level && rule.getChildCount() >= FEWEST_PARTS_OF_A_COMPARISON) {
                return previous != null && stopOf(rule) == previous && isReadCompletely(rule) ? rule : null;
            }
        }
        return null;
    }

    private static boolean isReadCompletely(ParserRuleContext comparison) {
        if (!RuleChecks.isComplete(comparison)) {
            return false;
        }
        for (var index = 0; index < comparison.getChildCount(); index++) {
            if (comparison.getChild(index) instanceof ParserRuleContext operand && !RuleChecks.isComplete(operand)) {
                return false;
            }
        }
        return true;
    }

    private static int levelOf(ParserRuleContext rule) {
        return switch (rule) {
        case EqualityContext ignored      -> EQUALITY;
        case HasExpressionContext ignored -> HAS;
        case ComparisonContext ignored    -> COMPARISON;
        default                           -> -1;
        };
    }

    /**
     * The text of the operand after the refused operator: the tokens up to the
     * next operator that binds as weakly or weaker, or the end of the
     * construct around the comparison.
     */
    private static @Nullable String operandAfter(Refusal refusal, int level) {
        val first = refusal.following();
        if (first == null || !startsAnOperand(first.getType())) {
            return null;
        }
        val scan     = new OperandScan(level);
        var last     = first;
        var distance = 2;
        var token    = refusal.afterRefused(distance);
        while (token != null && !scan.ends(token, last)) {
            last = token;
            distance++;
            token = refusal.afterRefused(distance);
        }
        val lines = refusal.lines();
        return refusal.text(Tokens.startOf(first, lines), Tokens.endOf(last, lines));
    }

    /**
     * Follows the tokens of an operand: brackets and attribute finders around
     * operators belong to the operand.
     */
    private static final class OperandScan {

        private final int           level;
        private final List<Integer> openAttributeFinders = new ArrayList<>();
        private int                 depth;

        OperandScan(int level) {
            this.level = level;
        }

        /**
         * @return whether the operand ends before the token
         */
        boolean ends(Token token, Token previous) {
            val type       = token.getType();
            val afterValue = Tokens.endsAnExpression(previous.getType());
            if (Bracket.openedBy(token) != null) {
                depth++;
                return false;
            }
            if (Bracket.closedBy(token) != null) {
                depth--;
                return depth < 0;
            }
            if (type == SAPLLexer.PIPE_LT || (type == SAPLLexer.LT && !afterValue)) {
                openAttributeFinders.add(depth);
                return false;
            }
            if (type == SAPLLexer.GT && !openAttributeFinders.isEmpty() && openAttributeFinders.getLast() == depth) {
                openAttributeFinders.removeLast();
                return false;
            }
            return depth == 0 && openAttributeFinders.isEmpty() && endsOperand(type, afterValue);
        }

        private boolean endsOperand(int type, boolean afterValue) {
            val precedence = PRECEDENCE.get(type);
            if (precedence != null) {
                return afterValue && precedence <= level;
            }
            return type == SAPLLexer.SEMI || type == SAPLLexer.COMMA || type == SAPLLexer.COLON
                    || Tokens.startsAPart(type) || Tokens.startsAPolicyPart(type);
        }
    }

    /**
     * Whether an operand can start with the token: a value, a group, an array, a
     * sign or an attribute finder.
     */
    private static boolean startsAnOperand(int type) {
        return Tokens.startsAValue(type) || type == SAPLLexer.LPAREN || type == SAPLLexer.LBRACKET
                || type == SAPLLexer.MINUS || type == SAPLLexer.PLUS || type == SAPLLexer.LT;
    }

    private static String textOf(Refusal refusal, ParseTree tree) {
        return textBetween(refusal, tree, tree);
    }

    private static String textBetween(Refusal refusal, ParseTree first, ParseTree last) {
        val lines = refusal.lines();
        return refusal.text(Tokens.startOf(startOf(first), lines), Tokens.endOf(stopOf(last), lines));
    }

    private static Token startOf(ParseTree tree) {
        return tree instanceof ParserRuleContext rule ? rule.getStart() : (Token) tree.getPayload();
    }

    private static Token stopOf(ParseTree tree) {
        return tree instanceof ParserRuleContext rule ? rule.getStop() : (Token) tree.getPayload();
    }

}
