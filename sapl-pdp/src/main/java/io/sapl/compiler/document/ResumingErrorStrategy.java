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

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import lombok.val;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * How the parser reacts to a token it cannot accept, what it tells about the
 * place, and where it goes on.
 * <p>
 * The parser leaves a construct that can end where the next token cannot
 * continue it. A parser of the kind that decides from the tokens read so far,
 * as the parsers of other implementations of SAPL do, leaves it only if the
 * token can follow the construct somewhere in the grammar, and refuses the
 * token otherwise. This parser refuses it there as well, so both refuse the
 * same token at the same place and accept the same tokens there. The place
 * where the parser first left a construct for a token is kept, so that the
 * accepted tokens can be told where it refuses the token further out.
 * <p>
 * After a refusal the parser goes on as close as it can: without the refused
 * token where the next one fits, and otherwise after the {@code ;} that ends
 * the statement, so that every part of a document is checked.
 */
final class ResumingErrorStrategy extends DefaultErrorStrategy {

    private int                         leftAtToken = -1;
    private int                         leftState;
    private @Nullable ParserRuleContext leftContext;

    @Override
    public void sync(Parser recognizer) {
        if (!inErrorRecoveryMode(recognizer)) {
            val next   = recognizer.getCurrentToken();
            val state  = recognizer.getInterpreter().atn.states.get(recognizer.getState());
            val within = recognizer.getATN().nextTokens(state);
            val leaves = !within.contains(next.getType()) && within.contains(Token.EPSILON);
            if (leaves && leftAtToken != next.getTokenIndex()) {
                leftAtToken = next.getTokenIndex();
                leftState   = recognizer.getState();
                leftContext = recognizer.getContext();
            }
            if (leaves && !ExpectedTokens.canFollow(state.ruleIndex, next.getType())) {
                refuseHere(recognizer, within);
                return;
            }
        }
        super.sync(recognizer);
    }

    /**
     * Refuses the next token where the construct cannot be left for it. Where
     * the token after it continues the construct, the parser goes on without
     * the refused one.
     */
    private void refuseHere(Parser recognizer, IntervalSet within) {
        if (within.contains(recognizer.getInputStream().LA(2))) {
            reportUnwantedToken(recognizer);
            recognizer.consume();
            return;
        }
        throw new InputMismatchException(recognizer);
    }

    @Override
    public Token recoverInline(Parser recognizer) {
        val matched = singleTokenDeletion(recognizer);
        if (matched != null) {
            recognizer.consume();
            return matched;
        }
        if (singleTokenInsertion(recognizer)) {
            return getMissingSymbol(recognizer);
        }
        val mismatch = nextTokensContext == null ? new InputMismatchException(recognizer)
                : new InputMismatchException(recognizer, nextTokensState, nextTokensContext);
        if (endsAStatement(recognizer) && endOfStatementFollows(recognizer)) {
            reportError(recognizer, mismatch);
            while (recognizer.getInputStream().LA(1) != SAPLLexer.SEMI) {
                recognizer.consume();
            }
            val end = recognizer.getCurrentToken();
            recognizer.consume();
            return end;
        }
        throw mismatch;
    }

    /**
     * Whether the parser expects the {@code ;} that ends a statement of a policy
     * or a variable definition of a policy set.
     */
    private static boolean endsAStatement(Parser recognizer) {
        val context = recognizer.getContext();
        val state   = recognizer.getInterpreter().atn.states.get(recognizer.getState());
        return (context instanceof PolicyBodyContext || context instanceof PolicySetContext)
                && recognizer.getATN().nextTokens(state).contains(SAPLLexer.SEMI);
    }

    /**
     * Whether a {@code ;} follows before the next part of the policy or the end
     * of the document.
     */
    private static boolean endOfStatementFollows(Parser recognizer) {
        // Looking ahead token by token through the stream would step over the
        // tokens before each one again.
        val input = recognizer.getInputStream();
        for (var index = input.index(); index < input.size(); index++) {
            val token = input.get(index);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            val type = token.getType();
            if (type == SAPLLexer.SEMI) {
                return true;
            }
            if (type == Token.EOF || Tokens.startsAPart(type)) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param parser the parser
     * @param refused the token the parser refused
     * @return the rule the parser was in where it first left a construct for the
     * refused token, or where it refused it
     */
    ParserRuleContext contextAt(Parser parser, Token refused) {
        return Objects.requireNonNullElseGet(leftFor(refused), parser::getContext);
    }

    /**
     * @param parser the parser
     * @param refused the token the parser refused
     * @param refusal what the parser raised, or null where it recovered without
     * @return the types of the tokens accepted in place of the refused token
     */
    IntervalSet expectedAt(Parser parser, Token refused, @Nullable RecognitionException refusal) {
        if (refusal instanceof NoViableAltException noAlternative) {
            return ExpectedTokens.atRefusedToken(parser, noAlternative);
        }
        val left = leftFor(refused);
        if (left != null) {
            return ExpectedTokens.whereRefused(leftState, left, refused.getType());
        }
        if (refusal != null) {
            return ExpectedTokens.whereRefused(refusal.getOffendingState(), refusal.getCtx(), refused.getType());
        }
        return ExpectedTokens.whereRefused(parser.getState(), parser.getContext(), refused.getType());
    }

    /**
     * @return the rule the parser was in where it first left a construct for the
     * refused token, or null if it left none for it
     */
    private @Nullable ParserRuleContext leftFor(Token refused) {
        return leftAtToken == refused.getTokenIndex() ? leftContext : null;
    }

}
