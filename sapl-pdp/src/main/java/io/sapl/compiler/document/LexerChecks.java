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
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Names what the lexer read as a mistake, and puts the token that was meant in
 * its place, so that the parser checks the rest of the document without
 * stumbling over the same mistake.
 * <p>
 * The lexer reads every text to its end: a string with an escape that does
 * not exist, a string or a comment that is not closed, a text in typographic
 * quotation marks and a character that starts no token each become a token of
 * their own. A string stands in for a string, an identifier for a name with
 * letters that names cannot have, and a number without its leading zeros for a
 * number with them. A comment that is not closed and a character that does not
 * belong to the language are kept for the editor, but the parser does not read
 * them.
 */
@UtilityClass
class LexerChecks {

    private static final String PLAIN_QUOTATION_MARK = "\"";
    private static final String SPACE                = " ";
    private static final int    COMMENT_OPENING      = 2;
    private static final String HEXADECIMAL          = "%04X";

    // The typographic quotation marks.
    private static final int LEFT_DOUBLE_ANGLE_QUOTATION_MARK      = 0x00AB;
    private static final int RIGHT_DOUBLE_ANGLE_QUOTATION_MARK     = 0x00BB;
    private static final int LEFT_SINGLE_QUOTATION_MARK            = 0x2018;
    private static final int DOUBLE_HIGH_REVERSED_9_QUOTATION_MARK = 0x201F;
    private static final int LEFT_SINGLE_ANGLE_QUOTATION_MARK      = 0x2039;
    private static final int RIGHT_SINGLE_ANGLE_QUOTATION_MARK     = 0x203A;

    // Invisible characters that are neither white space nor control characters.
    private static final int ARABIC_LETTER_MARK = 0x061C;
    private static final int ZERO_WIDTH_SPACE   = 0x200B;
    private static final int RIGHT_TO_LEFT_MARK = 0x200F;
    private static final int WORD_JOINER        = 0x2060;
    private static final int BYTE_ORDER_MARK    = 0xFEFF;

    private static final Map<Integer, Problem> INVISIBLE_CHARACTERS_WITH_NAMES = Map.of(0x0000, Problem.INVISIBLE_NULL,
            0x000B, Problem.INVISIBLE_VERTICAL_TAB, 0x000C, Problem.INVISIBLE_FORM_FEED, 0x00A0,
            Problem.INVISIBLE_NO_BREAK_SPACE, ZERO_WIDTH_SPACE, Problem.INVISIBLE_ZERO_WIDTH_SPACE, 0x2028,
            Problem.INVISIBLE_LINE_SEPARATOR, 0x2029, Problem.INVISIBLE_PARAGRAPH_SEPARATOR, 0x3000,
            Problem.INVISIBLE_IDEOGRAPHIC_SPACE, BYTE_ORDER_MARK, Problem.INVISIBLE_BYTE_ORDER_MARK);

    /**
     * Reads the tokens of the lexer. Once more problems are found than are
     * reported, it reads no further.
     *
     * @param lexed the tokens of the lexer, on every channel, ending with the end
     * of the document
     * @param lines the lines of the document
     * @param problems where the problems found are added
     * @return the tokens for the parser, on every channel
     */
    static List<Token> check(List<Token> lexed, DocumentLines lines, List<Diagnostic> problems) {
        val checked = new ArrayList<Token>(lexed.size());
        var index   = 0;
        while (index < lexed.size() && problems.size() <= SyntaxAnalysis.MAX_REPORTED_PROBLEMS) {
            val token = lexed.get(index);
            index = switch (token.getType()) {
            case SAPLLexer.STRING, SAPLLexer.MALFORMED_STRING ->
                withEscapesChecked(token, checked, lines, problems, index);
            case SAPLLexer.UNCLOSED_STRING                    -> unclosedString(token, checked, lines, problems, index);
            case SAPLLexer.TYPOGRAPHIC_STRING                 ->
                textInTypographicQuotes(token, checked, lines, problems, index);
            case SAPLLexer.UNCLOSED_COMMENT                   ->
                unclosedComment(token, checked, lines, problems, index);
            case SAPLLexer.UNKNOWN_CHARACTER                  ->
                foreignCharacter(lexed, index, checked, lines, problems);
            case SAPLLexer.NUMBER                             -> leadingZeros(lexed, index, checked, lines, problems);
            default                                           -> {
                checked.add(token);
                yield index + 1;
            }
            };
        }
        return checked;
    }

    private static int withEscapesChecked(Token string, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems, int index) {
        StringEscapes.problemOf(string, lines).ifPresent(problems::add);
        checked.add(Tokens.standIn(string, string, SAPLLexer.STRING, null));
        return index + 1;
    }

    private static int unclosedString(Token string, List<Token> checked, DocumentLines lines, List<Diagnostic> problems,
            int index) {
        problems.add(problem(Problem.UNTERMINATED_STRING, Tokens.regionOf(string, lines)));
        checked.add(Tokens.standIn(string, string, SAPLLexer.STRING, null));
        return index + 1;
    }

    private static int unclosedComment(Token comment, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems, int index) {
        val start = Tokens.startOf(comment, lines);
        problems.add(problem(Problem.UNTERMINATED_COMMENT, Region.of(lines, start, start + COMMENT_OPENING)));
        checked.add(Tokens.hidden(comment, SAPLLexer.ML_COMMENT));
        return index + 1;
    }

    /**
     * A typographic quotation mark and the text it opens, closed on the same
     * line. Where it is closed by a plain quotation mark, the opening one is
     * replaced. Where it is closed by a typographic one, the whole text is
     * written in plain quotation marks, unless a quotation mark or a backslash in
     * the text would then not stand for itself.
     */
    private static int textInTypographicQuotes(Token text, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems, int index) {
        val written    = text.getText();
        val openingEnd = Character.charCount(written.codePointAt(0));
        val closing    = written.codePointBefore(written.length());
        val content    = written.substring(openingEnd, written.length() - Character.charCount(closing));
        val start      = Tokens.startOf(text, lines);
        val opening    = Region.of(lines, start, start + openingEnd);
        val quickFix   = closing == '"' ? new QuickFix(opening, PLAIN_QUOTATION_MARK)
                : inPlainQuotationMarks(text, content, lines);
        problems.add(new Diagnostic(Problem.TYPOGRAPHIC_QUOTE, List.of(content), opening, List.of(), quickFix));
        checked.add(Tokens.standIn(text, text, SAPLLexer.STRING, null));
        return index + 1;
    }

    /**
     * The whole text in plain quotation marks, unless a quotation mark or a
     * backslash in the text would then not stand for itself.
     */
    private static @Nullable QuickFix inPlainQuotationMarks(Token text, String content, DocumentLines lines) {
        if (content.indexOf('"') >= 0 || content.indexOf('\\') >= 0) {
            return null;
        }
        return new QuickFix(Tokens.regionOf(text, lines), PLAIN_QUOTATION_MARK + content + PLAIN_QUOTATION_MARK);
    }

    /**
     * A character that starts no token. Where it is clear what was meant, that
     * is offered in its place.
     */
    private static int foreignCharacter(List<Token> lexed, int index, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems) {
        val token     = lexed.get(index);
        val character = token.getText().codePointAt(0);
        val region    = Tokens.regionOf(token, lines);
        if (isTypographicQuote(character)) {
            problems.add(new Diagnostic(Problem.TYPOGRAPHIC_QUOTE_WITHOUT_CONTENT, List.of(), region, List.of(),
                    new QuickFix(region, PLAIN_QUOTATION_MARK)));
        } else if (isInvisible(character)) {
            problems.add(invisible(character, region));
        } else if (isAlphanumeric(character)) {
            return invalidName(lexed, index, checked, lines, problems);
        } else {
            problems.add(new Diagnostic(Problem.UNEXPECTED_CHARACTER, List.of(Character.toString(character)), region,
                    List.of(), null));
        }
        checked.add(Tokens.hidden(token, SAPLLexer.UNKNOWN_CHARACTER));
        return index + 1;
    }

    private static Diagnostic invisible(int character, Region region) {
        val withName = INVISIBLE_CHARACTERS_WITH_NAMES.get(character);
        val quickFix = new QuickFix(region, SPACE);
        if (withName != null) {
            return new Diagnostic(withName, List.of(), region, List.of(), quickFix);
        }
        return new Diagnostic(Problem.INVISIBLE_CHARACTER, List.of(hexadecimal(character)), region, List.of(),
                quickFix);
    }

    /**
     * A word that holds letters or digits which names cannot have, such as an
     * umlaut. The whole word is one name, and an identifier stands in for it.
     * The letters before the foreign one were read as a token of their own and
     * belong to the word, as do the tokens after it up to the end of the word.
     */
    private static int invalidName(List<Token> lexed, int index, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems) {
        var first = lexed.get(index);
        if (!checked.isEmpty() && Tokens.isWord(checked.getLast().getType())
                && Tokens.adjacent(checked.getLast(), first)) {
            first = checked.removeLast();
        }
        val text    = lines.text();
        var wordEnd = Tokens.startOf(lexed.get(index), lines);
        while (wordEnd < text.length() && isPartOfWord(text.codePointAt(wordEnd))) {
            wordEnd += Character.charCount(text.codePointAt(wordEnd));
        }
        var last = index;
        while (last + 1 < lexed.size() && lexed.get(last + 1).getType() != Token.EOF
                && Tokens.startOf(lexed.get(last + 1), lines) < wordEnd) {
            last++;
        }
        val name   = Tokens.standIn(first, lexed.get(last), SAPLLexer.ID, null);
        val region = Tokens.regionOf(name, lines);
        problems.add(invalidNameProblem(name, region, checked, lines));
        checked.add(name);
        return last + 1;
    }

    /**
     * After a dot the name is a key, and a key of any text can be selected as a
     * string.
     */
    private static Diagnostic invalidNameProblem(Token name, Region region, List<Token> checked, DocumentLines lines) {
        val written = name.getText();
        if (!checked.isEmpty() && checked.getLast().getType() == SAPLLexer.DOT
                && Tokens.adjacent(checked.getLast(), name)) {
            val dot       = checked.getLast();
            val selection = "[\"" + written + "\"]";
            val path      = Tokens.pathBefore(checked, checked.size() - 1, dot, lines);
            val quickFix  = new QuickFix(Region.of(lines, Tokens.startOf(dot, lines), region.end()), selection);
            return new Diagnostic(Problem.INVALID_KEY_NAME, List.of(written, path + selection), region, List.of(),
                    quickFix);
        }
        return new Diagnostic(Problem.INVALID_NAME, List.of(written), region, List.of(), null);
    }

    /**
     * The lexer reads a number with leading zeros as several numbers that follow
     * each other directly: each leading zero is a number of its own. They are
     * one number, and the number without its leading zeros stands in for it.
     */
    private static int leadingZeros(List<Token> lexed, int index, List<Token> checked, DocumentLines lines,
            List<Diagnostic> problems) {
        var last = index;
        while (last + 1 < lexed.size() && lexed.get(last + 1).getType() == SAPLLexer.NUMBER
                && Tokens.adjacent(lexed.get(last), lexed.get(last + 1))) {
            last++;
        }
        if (last == index) {
            checked.add(lexed.get(index));
            return index + 1;
        }
        val written    = Tokens.standIn(lexed.get(index), lexed.get(last), SAPLLexer.NUMBER, null).getText();
        val suggestion = withoutLeadingZeros(written);
        val number     = Tokens.standIn(lexed.get(index), lexed.get(last), SAPLLexer.NUMBER, suggestion);
        val region     = Tokens.regionOf(number, lines);
        problems.add(new Diagnostic(Problem.LEADING_ZERO, List.of(suggestion), region, List.of(),
                new QuickFix(region, suggestion)));
        checked.add(number);
        return last + 1;
    }

    /**
     * The number without the zeros before its first significant digit. If only
     * zeros stand before the point or the end of the number, one of them is
     * kept.
     */
    private static String withoutLeadingZeros(String number) {
        var leadingZeros = 0;
        while (leadingZeros < number.length() && number.charAt(leadingZeros) == '0') {
            leadingZeros++;
        }
        if (leadingZeros < number.length() && Character.isDigit(number.charAt(leadingZeros))) {
            return number.substring(leadingZeros);
        }
        return number.substring(leadingZeros - 1);
    }

    private static Diagnostic problem(Problem problem, Region region) {
        return new Diagnostic(problem, List.of(), region, List.of(), null);
    }

    /**
     * @return the code point of a character in hexadecimal, with at least four
     * digits, as the catalogue names a character
     */
    static String hexadecimal(int character) {
        return HEXADECIMAL.formatted(character);
    }

    private static boolean isTypographicQuote(int character) {
        return character == LEFT_DOUBLE_ANGLE_QUOTATION_MARK || character == RIGHT_DOUBLE_ANGLE_QUOTATION_MARK
                || (character >= LEFT_SINGLE_QUOTATION_MARK && character <= DOUBLE_HIGH_REVERSED_9_QUOTATION_MARK)
                || character == LEFT_SINGLE_ANGLE_QUOTATION_MARK || character == RIGHT_SINGLE_ANGLE_QUOTATION_MARK;
    }

    /**
     * Whether an editor shows nothing or only space for the character: white
     * space, a control character, or a character that only steers how text is
     * written.
     */
    private static boolean isInvisible(int character) {
        return Character.isSpaceChar(character) || Character.getType(character) == Character.CONTROL
                || character == ARABIC_LETTER_MARK || (character >= ZERO_WIDTH_SPACE && character <= RIGHT_TO_LEFT_MARK)
                || character == WORD_JOINER || character == BYTE_ORDER_MARK;
    }

    private static boolean isAlphanumeric(int character) {
        val type = Character.getType(character);
        return Character.isAlphabetic(character) || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    private static boolean isPartOfWord(int character) {
        return isAlphanumeric(character) || character == '_' || character == '$';
    }

}
