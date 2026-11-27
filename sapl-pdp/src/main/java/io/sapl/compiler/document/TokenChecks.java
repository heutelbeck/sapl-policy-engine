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
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.FurtherRegion;
import io.sapl.compiler.diagnostics.FurtherRegion.Label;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.document.Tokens.Bracket;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks on the tokens of a document before they are parsed.
 * <p>
 * Some mistakes show in the tokens next to each other more reliably than in
 * the grammar: numbers in a form SAPL does not know, and brackets that do not
 * match. Each is reported once, and the tokens are corrected, so that the
 * parser checks the rest of the document without stumbling over the same
 * mistake.
 */
@UtilityClass
class TokenChecks {

    /**
     * Reports malformed numbers and mismatched brackets and returns the tokens
     * without them.
     *
     * @param tokens the tokens for the parser, on every channel, ending with the
     * end of the document
     * @param lines the lines of the document
     * @param problems where the problems found are added
     * @return the corrected tokens, or null if the checks gave up before the end
     * of the document: where brackets are nested beyond the limit, and when more
     * problems are found than are reported. Checking on would cost time and
     * memory without telling the author more.
     */
    static @Nullable List<Token> check(List<Token> tokens, DocumentLines lines, List<Diagnostic> problems) {
        val withNumbersChecked = checkNumbers(tokens, lines, problems);
        val checked            = checkBrackets(withNumbersChecked, lines, problems);
        return problems.size() <= SyntaxAnalysis.MAX_REPORTED_PROBLEMS ? checked : null;
    }

    /**
     * A number is {@code 0} or starts with a digit from 1 to 9, and a point is
     * followed and preceded by digits. Whatever is written differently reaches
     * this check as a number next to a dot or a name. It is reported, and the
     * number that was meant takes its place.
     */
    private static List<Token> checkNumbers(List<Token> tokens, DocumentLines lines, List<Diagnostic> problems) {
        val checked = new ArrayList<Token>(tokens.size());
        var index   = 0;
        while (index < tokens.size() && problems.size() <= SyntaxAnalysis.MAX_REPORTED_PROBLEMS) {
            val current   = tokens.get(index);
            val following = directlyFollowing(tokens, index);
            index++;
            if (following == null || !isMalformedNumber(current, following, tokens, index)) {
                checked.add(current);
                continue;
            }
            if (current.getType() == SAPLLexer.DOT) {
                digitsAfterDot(checked, current, following, lines, problems);
            } else {
                problems.add(numberProblem(current, following, lines));
                checked.add(Tokens.standIn(current, following, SAPLLexer.NUMBER, current.getText()));
            }
            // The following token was part of the mistake.
            index++;
        }
        return checked;
    }

    /**
     * Letters after the digits, as in {@code 10px}, a point after the digits, as
     * in {@code 1.}, and digits after a dot. A dot that a key follows is a step
     * applied to the number, as in {@code 1.length}.
     */
    private static boolean isMalformedNumber(Token current, Token following, List<Token> tokens, int afterCurrent) {
        val type = current.getType();
        return switch (following.getType()) {
        case SAPLLexer.ID     -> type == SAPLLexer.NUMBER;
        case SAPLLexer.DOT    -> type == SAPLLexer.NUMBER && !keyFollows(tokens, afterCurrent);
        case SAPLLexer.NUMBER -> type == SAPLLexer.DOT;
        default               -> false;
        };
    }

    private static Diagnostic numberProblem(Token number, Token following, DocumentLines lines) {
        val region = Tokens.regionOf(number, following, lines);
        if (following.getType() == SAPLLexer.ID) {
            val literal = Tokens.textOf(lines, region.start(), region.end());
            return new Diagnostic(Problem.NUMBER_WITH_LETTERS, List.of(literal), region, List.of(), null);
        }
        return new Diagnostic(Problem.NUMBER_ENDS_WITH_POINT, List.of(number.getText()), region, List.of(), null);
    }

    /**
     * @return the token that the parser reads after the one at an index, if it
     * follows that one without any character between them
     */
    private static @Nullable Token directlyFollowing(List<Token> tokens, int index) {
        if (index + 1 >= tokens.size()) {
            return null;
        }
        val current   = tokens.get(index);
        val following = tokens.get(index + 1);
        val readable  = current.getChannel() == Token.DEFAULT_CHANNEL && following.getChannel() == Token.DEFAULT_CHANNEL
                && following.getType() != Token.EOF;
        return readable && Tokens.adjacent(current, following) ? following : null;
    }

    /**
     * Whether a key follows a dot directly, as in {@code subject.name} or
     * {@code subject.*}.
     */
    private static boolean keyFollows(List<Token> tokens, int indexOfDot) {
        val afterDot = directlyFollowing(tokens, indexOfDot);
        if (afterDot == null) {
            return false;
        }
        val type = afterDot.getType();
        return Tokens.isName(type) || type == SAPLLexer.STAR || type == SAPLLexer.LT || type == SAPLLexer.PIPE_LT;
    }

    /**
     * Digits that follow a dot directly. After a value they are an index written
     * like a key, as in {@code list.0}. Anywhere else they are a number that
     * starts with its point, as in {@code .5}. The tokens of what was meant take
     * their place.
     */
    private static void digitsAfterDot(List<Token> checked, Token dot, Token number, DocumentLines lines,
            List<Diagnostic> problems) {
        val region       = Tokens.regionOf(dot, number, lines);
        val digits       = number.getText();
        val previous     = checked.isEmpty() ? null : checked.getLast();
        val followsValue = previous != null && previous.getChannel() == Token.DEFAULT_CHANNEL
                && Tokens.adjacent(previous, dot) && Tokens.endsAValue(previous.getType());
        if (followsValue) {
            val subscript = "[" + digits + "]";
            val path      = Tokens.pathBefore(checked, checked.size(), dot, lines);
            problems.add(new Diagnostic(Problem.INDEX_AFTER_DOT, List.of(path + subscript), region, List.of(),
                    new QuickFix(region, subscript)));
            checked.add(Tokens.standIn(dot, dot, SAPLLexer.LBRACKET, "["));
            checked.add(number);
            checked.add(Tokens.added(number, number.getStopIndex() + 1, SAPLLexer.RBRACKET, ""));
        } else {
            problems.add(new Diagnostic(Problem.NUMBER_STARTS_WITH_POINT, List.of(digits), region, List.of(),
                    new QuickFix(region, "0." + digits)));
            checked.add(Tokens.standIn(dot, number, SAPLLexer.NUMBER, digits));
        }
    }

    /**
     * An opening bracket that is not closed yet.
     */
    private record OpenBracket(Bracket kind, Region region) {}

    /**
     * Every bracket is closed by its own kind, in the reverse order of opening,
     * and before the statement around it ends. Where it is not, that is
     * reported, and the brackets are set right.
     *
     * @return null where brackets are nested beyond the limit. The limit bounds
     * the depth of the parse tree, so a document beyond it is refused and not
     * checked further.
     */
    private static @Nullable List<Token> checkBrackets(List<Token> tokens, DocumentLines lines,
            List<Diagnostic> problems) {
        val checked      = new ArrayList<Token>(tokens.size());
        val openBrackets = new ArrayList<OpenBracket>();
        for (val current : tokens) {
            if (problems.size() > SyntaxAnalysis.MAX_REPORTED_PROBLEMS) {
                return checked;
            }
            val opened = Bracket.openedBy(current);
            val closed = Bracket.closedBy(current);
            if (current.getChannel() != Token.DEFAULT_CHANNEL) {
                checked.add(current);
            } else if (opened != null) {
                if (openBrackets.size() == DocumentCompiler.MAX_NESTING_DEPTH) {
                    problems.add(new Diagnostic(Problem.NESTING_TOO_DEEP,
                            List.of(String.valueOf(DocumentCompiler.MAX_NESTING_DEPTH)),
                            Tokens.regionOf(current, lines), List.of(), null));
                    return null;
                }
                openBrackets.add(new OpenBracket(opened, Tokens.regionOf(current, lines)));
                checked.add(current);
            } else if (closed != null) {
                closeBracket(current, closed, openBrackets, checked, lines, problems);
            } else {
                endOfConstruct(current, openBrackets, checked, lines, problems);
                checked.add(current);
            }
        }
        return checked;
    }

    /**
     * The brackets are closed before the statement around them ends, and before
     * the document ends.
     */
    private static void endOfConstruct(Token current, List<OpenBracket> openBrackets, List<Token> checked,
            DocumentLines lines, List<Diagnostic> problems) {
        if (current.getType() == SAPLLexer.SEMI) {
            closeMissing(openBrackets, checked, lines, problems);
            openBrackets.clear();
        } else if (current.getType() == Token.EOF) {
            closeAtEnd(openBrackets, current, checked, problems);
        }
    }

    private static void closeBracket(Token current, Bracket found, List<OpenBracket> openBrackets, List<Token> checked,
            DocumentLines lines, List<Diagnostic> problems) {
        var matching = openBrackets.size() - 1;
        while (matching >= 0 && openBrackets.get(matching).kind() != found) {
            matching--;
        }
        if (matching >= 0) {
            // The brackets opened inside the matching one were not closed.
            val openedInside = openBrackets.subList(matching + 1, openBrackets.size());
            closeMissing(openedInside, checked, lines, problems);
            openedInside.clear();
            openBrackets.removeLast();
            checked.add(current);
        } else if (!openBrackets.isEmpty()) {
            // No open bracket is of the kind found. The innermost one is taken to be
            // closed by a bracket of the wrong kind.
            val innermost = openBrackets.removeLast();
            val kind      = innermost.kind();
            val region    = Tokens.regionOf(current, lines);
            val values    = List.of(found.closing(), kind.opening(), String.valueOf(innermost.region().line()),
                    String.valueOf(innermost.region().column()), kind.closing());
            problems.add(new Diagnostic(Problem.MISMATCHED_BRACKET, values, region, openedHere(innermost),
                    new QuickFix(region, kind.closing())));
            checked.add(Tokens.standIn(current, current, kind.closingType(), kind.closing()));
        } else {
            // Nothing is open. The parser goes on without the bracket.
            problems.add(new Diagnostic(Problem.UNMATCHED_CLOSING_BRACKET, List.of(found.closing(), found.opening()),
                    Tokens.regionOf(current, lines), List.of(), null));
            checked.add(Tokens.hidden(current, current.getType()));
        }
    }

    /**
     * Reports brackets that are still open where the construct around them
     * ends, and closes them. The mark is on the last token before the place
     * where the closing brackets are missing.
     */
    private static void closeMissing(List<OpenBracket> unclosed, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems) {
        var lastInside = checked.size() - 1;
        while (lastInside >= 0 && checked.get(lastInside).getChannel() != Token.DEFAULT_CHANNEL) {
            lastInside--;
        }
        if (unclosed.isEmpty() || lastInside < 0) {
            return;
        }
        val last      = checked.get(lastInside);
        val innermost = unclosed.getLast();
        val missing   = new StringBuilder();
        val closing   = new ArrayList<Token>();
        for (var index = unclosed.size() - 1; index >= 0; index--) {
            val kind = unclosed.get(index).kind();
            missing.append(kind.closing());
            closing.add(Tokens.added(last, last.getStopIndex() + 1, kind.closingType(), ""));
        }
        val region = Tokens.regionOf(last, lines);
        val values = List.of(innermost.kind().closing(), innermost.kind().opening(),
                String.valueOf(innermost.region().line()), String.valueOf(innermost.region().column()));
        problems.add(new Diagnostic(Problem.MISSING_CLOSING_BRACKET, values, region, openedHere(innermost),
                new QuickFix(Region.of(lines, region.end(), region.end()), missing.toString())));
        checked.addAll(lastInside + 1, closing);
    }

    /**
     * Of the brackets that are open at the end of the document the innermost is
     * reported, and all are closed there.
     */
    private static void closeAtEnd(List<OpenBracket> openBrackets, Token end, List<Token> checked,
            List<Diagnostic> problems) {
        if (openBrackets.isEmpty()) {
            return;
        }
        val innermost = openBrackets.getLast();
        problems.add(new Diagnostic(Problem.UNCLOSED_BRACKET,
                List.of(innermost.kind().opening(), innermost.kind().closing()), innermost.region(), List.of(), null));
        for (var index = openBrackets.size() - 1; index >= 0; index--) {
            val kind = openBrackets.get(index).kind();
            checked.add(Tokens.added(end, end.getStartIndex(), kind.closingType(), ""));
        }
        openBrackets.clear();
    }

    private static List<FurtherRegion> openedHere(OpenBracket bracket) {
        return List.of(new FurtherRegion(bracket.region(), Label.OPENED_HERE));
    }

}
