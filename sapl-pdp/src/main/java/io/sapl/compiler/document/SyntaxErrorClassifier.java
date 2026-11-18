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
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells what is wrong where the parser refuses a token, in the terms of the
 * catalogue of problems.
 * <p>
 * The parser only tells which token it refused and which tokens it would have
 * accepted instead. The accepted tokens tell where in the grammar it was. From
 * there a mistake is one of two kinds:
 * <ul>
 * <li>The refused token is wrong: an operator of another language, a keyword
 * used as a name, a part of a policy in the wrong place ({@link WrongTokens}).</li>
 * <li>Something is missing before the refused token: a name, a value, a
 * {@code ;} ({@link MissingTokens}).</li>
 * </ul>
 * Where no known mistake fits, the refused token is reported with what is
 * expected instead.
 */
@UtilityClass
class SyntaxErrorClassifier {

    private static final int[] DELIMITERS = { SAPLLexer.SEMI, SAPLLexer.COMMA, SAPLLexer.RPAREN, SAPLLexer.RBRACKET,
            SAPLLexer.RBRACE };

    /**
     * @param refusal where the parser refused a token
     * @return the problem of the catalogue
     */
    static Diagnostic classify(Refusal refusal) {
        return WrongTokens.of(refusal).or(() -> MissingTokens.before(refusal)).orElseGet(() -> unexpected(refusal));
    }

    /**
     * What is reported when no known mistake fits: the refused token and what is
     * expected instead.
     */
    private static Diagnostic unexpected(Refusal refusal) {
        if (refusal.followsAValue()) {
            val delimiters = new ArrayList<String>();
            for (val delimiter : DELIMITERS) {
                if (refusal.expected().contains(delimiter)) {
                    delimiters.add(literalOf(delimiter));
                }
            }
            if (delimiters.isEmpty()) {
                return unexpected(refusal, Problem.UNEXPECTED_AFTER_VALUE, Problem.UNEXPECTED_END_AFTER_VALUE);
            }
            return unexpected(refusal, Problem.UNEXPECTED_AFTER_VALUE_WITH_DELIMITERS,
                    Problem.UNEXPECTED_END_AFTER_VALUE_WITH_DELIMITERS, Problem.delimiters(delimiters));
        }
        if (refusal.expected().aValue()) {
            return unexpected(refusal, Problem.UNEXPECTED_WHERE_VALUE_EXPECTED,
                    Problem.UNEXPECTED_END_WHERE_VALUE_EXPECTED);
        }
        val alternatives = alternativesOf(refusal);
        if (alternatives.isEmpty()) {
            return unexpected(refusal, Problem.UNEXPECTED_WHERE_END_EXPECTED,
                    Problem.UNEXPECTED_END_WHERE_END_EXPECTED);
        }
        return unexpected(refusal, Problem.UNEXPECTED_WHERE_ONE_OF_EXPECTED,
                Problem.UNEXPECTED_END_WHERE_ONE_OF_EXPECTED, Problem.oneOf(alternatives));
    }

    /**
     * @param ofToken the problem where a token is refused, which names the token
     * first
     * @param ofEnd the problem where the document ends
     * @param expected what is expected instead, if the problems name it
     */
    private static Diagnostic unexpected(Refusal refusal, Problem ofToken, Problem ofEnd, String... expected) {
        val refused = refusal.refused();
        if (refused == null) {
            return Found.of(ofEnd, expected).atRefused(refusal);
        }
        val values = new ArrayList<String>();
        values.add(refused.getText());
        values.addAll(List.of(expected));
        return new Found(ofToken, values).atRefused(refusal);
    }

    /**
     * What the parser accepts in place of the refused token, in the terms of the
     * language. Where a name is accepted, the keywords that can be used as names
     * are names.
     */
    private static List<String> alternativesOf(Refusal refusal) {
        val namesAreAccepted = refusal.expected().contains(SAPLLexer.ID);
        val alternatives     = new ArrayList<String>();
        for (val type : refusal.expected().types().toList()) {
            val alternative = alternativeOf(type, namesAreAccepted);
            if (!alternatives.contains(alternative)) {
                alternatives.add(alternative);
            }
        }
        return alternatives;
    }

    private static String alternativeOf(int type, boolean namesAreAccepted) {
        if (type == SAPLLexer.ID || type == SAPLLexer.BACKTICK_ID
                || (namesAreAccepted && Tokens.isKeywordUsableAsName(type))) {
            return Problem.A_NAME;
        }
        if (type == SAPLLexer.STRING) {
            return Problem.A_TEXT;
        }
        if (type == SAPLLexer.NUMBER) {
            return Problem.A_NUMBER;
        }
        return Problem.token(literalOf(type));
    }

    /**
     * @return a keyword or a symbol as it is written in a document
     */
    private static String literalOf(int type) {
        val literal = SAPLLexer.VOCABULARY.getLiteralName(type);
        if (literal == null) {
            return SAPLLexer.VOCABULARY.getSymbolicName(type);
        }
        return literal.substring(1, literal.length() - 1);
    }

}
