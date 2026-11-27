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
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.document.Refusal.Expected;
import io.sapl.compiler.document.Refusal.ReadTokens;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.util.SourceLocationUtil;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.sapl.compiler.util.TrojanSourceUtil.indexOfTrojanSourceCharacter;

/**
 * The syntax of a SAPL document: its tokens, its parse tree, and the problems
 * of its syntax and of the rules that the grammar cannot express. The compiler
 * and the language server read a document through it, so both report the same
 * problems at the same places.
 * <p>
 * A document that is too large is not read, and has this one problem. So has
 * a document that reading fails on in a way that the checks did not foresee. A
 * document that holds a bidirectional control character has this one problem
 * and no other. Any other document has the problems of its tokens, of its parse
 * and of its rules, in the order of their places: at most one problem of the
 * parse or the rules in each part of the document, at most
 * {@value #MAX_REPORTED_PROBLEMS} problems, and a notice if there are more. A
 * document whose brackets are nested too deeply, or that has more problems in
 * its tokens than are reported, is not parsed.
 *
 * @param source the text of the document
 * @param tokenStream the tokens the parser read, read to the end. Where a token
 * stands for a mistake, it is the token that was meant, and a token that was
 * missing takes no space. Empty if the document is too large to be read.
 * @param parseTree the parse tree, or null if the document was not parsed
 * @param syntaxIsValid whether the tokens and the parse have no problem, so
 * that the parse tree is complete, whatever the rules say
 * @param diagnostics the problems, in the order of their places
 */
@Slf4j
public record SyntaxAnalysis(
        String source,
        CommonTokenStream tokenStream,
        @Nullable SaplContext parseTree,
        boolean syntaxIsValid,
        List<Diagnostic> diagnostics) {

    private static final String ERROR_UNFORESEEN_FAILURE = "Reading a SAPL document failed in a way that its checks did not foresee. The document is refused.";

    /**
     * The most problems that are reported for one document. If a document has
     * more, the report ends with a notice that says so. Nobody works through more
     * at once, and a hostile document must not be answered with a report many
     * times its own size.
     */
    static final int MAX_REPORTED_PROBLEMS = 100;

    /**
     * A region for a problem that has no place of its own: the start of the
     * document.
     */
    private static final Region START_OF_DOCUMENT = new Region(0, 0, 1, 1, 1, 1);

    private static final Comparator<Diagnostic> BY_PLACE = Comparator
            .comparingInt(diagnostic -> diagnostic.region().start());

    /**
     * Keeps the problems as they are found, whatever the caller does with its
     * list afterwards.
     */
    public SyntaxAnalysis {
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Reads and parses a document and finds the problems of its syntax and of
     * its rules.
     *
     * @param source the text of the document
     * @return the analysis of the document
     */
    public static SyntaxAnalysis of(String source) {
        val size = source.getBytes(StandardCharsets.UTF_8).length;
        if (size > DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES) {
            return refused(source, tooLarge(source, size));
        }
        return refusingUnforeseenFailures(source, () -> analysed(source), problem -> refused(source, problem));
    }

    /**
     * Runs a step of reading a document: its analysis, or the transformation of
     * a document whose analysis found no problem. The checks of the analysis
     * refuse every document that a step cannot read, so a step that throws has
     * met a document that the checks did not foresee. Such a failure does not
     * reach the caller. It is logged, and the document is refused with
     * {@link Problem#SYNTAX_ERROR}, or with
     * {@link Problem#NESTING_TOO_DEEP_WITHOUT_BRACKETS}
     * where the step exhausted the stack.
     *
     * @param source the text of the document
     * @param step the step
     * @param refusal what the step returns for a document that it refuses with
     * a problem
     * @param <T> what the step returns
     * @return what the step returns, or the refusal
     */
    static <T> T refusingUnforeseenFailures(String source, Supplier<T> step, Function<Diagnostic, T> refusal) {
        try {
            return step.get();
        } catch (StackOverflowError overflow) {
            log.error(ERROR_UNFORESEEN_FAILURE, overflow);
            return refusal.apply(nestingTooDeep());
        } catch (RuntimeException failure) {
            log.error(ERROR_UNFORESEEN_FAILURE, failure);
            return refusal.apply(unforeseen(failure, source));
        }
    }

    private static SyntaxAnalysis analysed(String source) {
        val lexed = tokenStreamOf(source);
        lexed.fill();
        val lines    = linesOf(lexed.get(0), source);
        val problems = new ArrayList<Diagnostic>();
        val readable = LexerChecks.check(lexed.getTokens(), lines, problems);
        val checked  = problems.size() <= MAX_REPORTED_PROBLEMS ? TokenChecks.check(readable, lines, problems) : null;
        if (checked == null) {
            return withoutBidirectionalControls(
                    new SyntaxAnalysis(source, lexed, null, false, reportOf(problems, false)), lines);
        }
        val tokenStream = new CommonTokenStream(new ListTokenSource(checked));
        tokenStream.fill();
        val read     = ReadTokens.of(tokenStream.getTokens().stream()
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF).toList(),
                lines);
        val parts    = Parts.of(read.tokens(), read.lines());
        val parser   = new SAPLParser(tokenStream);
        val strategy = new ResumingErrorStrategy();
        val recorder = new RefusalRecorder(strategy, read, parts.withProblemsOf(problems));
        parser.setErrorHandler(strategy);
        parser.removeErrorListeners();
        parser.addErrorListener(recorder);
        val parseTree = parsed(parser);
        if (parseTree == null && !recorder.gaveUp()) {
            problems.add(nestingTooDeep());
        }
        val syntaxIsValid = problems.isEmpty() && recorder.problems().isEmpty();
        val ofRules       = parseTree == null ? List.<Diagnostic>of() : RuleChecks.check(parseTree, lines);
        problems.addAll(firstOfEachPart(recorder.problems(), ofRules, parts.withProblemsOf(problems)));
        return withoutBidirectionalControls(new SyntaxAnalysis(source, tokenStream, parseTree, syntaxIsValid,
                reportOf(problems, recorder.gaveUp())), lines);
    }

    /**
     * A failure of reading a document that the checks did not foresee. A
     * failure of the transformation that names its place is marked there, any
     * other failure at the start of the document.
     *
     * @param failure the failure
     * @param source the text of the document
     * @return the problem, which passes on the message of the transformation, or
     * the kind and the message of any other failure
     */
    private static Diagnostic unforeseen(RuntimeException failure, String source) {
        if (failure instanceof SaplCompilerException transformation && transformation.getMessage() != null) {
            val location = transformation.getLocation();
            val region   = location == null ? START_OF_DOCUMENT
                    : Region.of(DocumentLines.of(source), location.start(), location.end());
            return problem(Problem.SYNTAX_ERROR, region, transformation.getMessage());
        }
        return problem(Problem.SYNTAX_ERROR, START_OF_DOCUMENT, failure.toString());
    }

    /**
     * @return a document that is not read, with its one problem
     */
    private static SyntaxAnalysis refused(String source, Diagnostic problem) {
        return new SyntaxAnalysis(source, tokenStreamOf(""), null, false, List.of(problem));
    }

    /**
     * @return the problem of input that nests deeply without brackets, such as a
     * long chain of prefix operators. It is marked at the start of the document,
     * as no place in it is known.
     */
    static Diagnostic nestingTooDeep() {
        return problem(Problem.NESTING_TOO_DEEP_WITHOUT_BRACKETS, START_OF_DOCUMENT);
    }

    private static Diagnostic problem(Problem problem, Region region, String... values) {
        return new Diagnostic(problem, List.of(values), region, List.of(), null);
    }

    private static Diagnostic tooLarge(String source, int size) {
        val region = Region.of(DocumentLines.of(source), 0, source.length());
        return problem(Problem.DOCUMENT_TOO_LARGE, region, String.valueOf(size),
                String.valueOf(DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES));
    }

    /**
     * @return the lines of the document a token was read from, counted once per
     * parse
     */
    private static DocumentLines linesOf(Token token, String source) {
        return Objects.requireNonNullElseGet(SourceLocationUtil.documentLinesOf(token), () -> DocumentLines.of(source));
    }

    private static CommonTokenStream tokenStreamOf(String source) {
        val lexer = new SAPLLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        return new CommonTokenStream(lexer);
    }

    /**
     * Input that nests deeply without brackets, such as a long chain of prefix
     * operators, overflows the recursive-descent parser although it is within
     * the nesting limit.
     */
    private static @Nullable SaplContext parsed(SAPLParser parser) {
        try {
            return parser.sapl();
        } catch (StackOverflowError | ParseCancellationException e) {
            return null;
        }
    }

    /**
     * A bidirectional control character can make a document display
     * differently from how it is evaluated. Whatever else the document holds,
     * this is its one problem. Its tokens and its parse tree are kept for the
     * editor.
     */
    private static SyntaxAnalysis withoutBidirectionalControls(SyntaxAnalysis analysis, DocumentLines lines) {
        val offset = indexOfTrojanSourceCharacter(analysis.source());
        if (offset < 0) {
            return analysis;
        }
        val control = problem(Problem.BIDIRECTIONAL_CONTROL, Region.of(lines, offset, offset + 1),
                LexerChecks.hexadecimal(analysis.source().charAt(offset)));
        return new SyntaxAnalysis(analysis.source(), analysis.tokenStream(), analysis.parseTree(), false,
                List.of(control));
    }

    /**
     * The problems of the parse and of the rules. A part of the document that
     * has a problem already gets no further one: one mistake often makes the
     * parser stumble again further on in the same part, and what it finds there
     * would only repeat the first problem in other words.
     */
    private static List<Diagnostic> firstOfEachPart(List<Diagnostic> ofParse, List<Diagnostic> ofRules, Parts parts) {
        val candidates = new ArrayList<>(ofRules);
        candidates.addAll(ofParse);
        candidates.sort(BY_PLACE);
        return candidates.stream().filter(parts::takes).toList();
    }

    /**
     * The report of a document: its problems in the order of the document. After
     * {@value #MAX_REPORTED_PROBLEMS} problems it ends with a notice where the
     * report stops. Where the parser gave up before the end of the document, the
     * report stops with the last problem found.
     */
    private static List<Diagnostic> reportOf(List<Diagnostic> problems, boolean parserGaveUp) {
        val sorted = new ArrayList<>(problems);
        sorted.sort(BY_PLACE);
        if (sorted.size() > MAX_REPORTED_PROBLEMS) {
            val firstLeftOut = sorted.get(MAX_REPORTED_PROBLEMS).region();
            val report       = new ArrayList<>(sorted.subList(0, MAX_REPORTED_PROBLEMS));
            report.add(tooManyProblems(firstLeftOut, report.size()));
            return report;
        }
        if (parserGaveUp && !sorted.isEmpty()) {
            sorted.add(tooManyProblems(sorted.getLast().region(), sorted.size()));
        }
        return sorted;
    }

    private static Diagnostic tooManyProblems(Region stoppedAt, int reported) {
        return problem(Problem.TOO_MANY_PROBLEMS, stoppedAt, String.valueOf(reported));
    }

    /**
     * Tells what the parser refuses, in the terms of the catalogue, while it
     * parses: the refused token, and the tokens the parser would have accepted
     * there, which tell where in the grammar it was.
     * <p>
     * The parser gives up once it has found more problems than are reported, or
     * refused more tokens than a document may make it refuse. Every refusal costs
     * time, also one that is not reported because its part of the document has a
     * problem already, and going on would not tell the author more.
     */
    private static final class RefusalRecorder extends BaseErrorListener {

        /**
         * A part of a document with a problem can make the parser refuse further
         * tokens that are not reported. The parser gives up after this many
         * refusals for each problem that is reported.
         */
        private static final int REFUSALS_PER_REPORTED_PROBLEM = 10;
        private static final int MAX_REFUSALS                  = REFUSALS_PER_REPORTED_PROBLEM * MAX_REPORTED_PROBLEMS;

        private final ResumingErrorStrategy strategy;
        private final ReadTokens            read;
        private final Parts                 parts;
        private final List<Diagnostic>      problems = new ArrayList<>();
        private int                         refusals;
        private int                         firstOfTheirParts;
        private boolean                     gaveUp;

        /**
         * @param parts the parts of the document, of which those that hold a
         * problem found before the parse have a problem
         */
        RefusalRecorder(ResumingErrorStrategy strategy, ReadTokens read, Parts parts) {
            this.strategy = strategy;
            this.read     = read;
            this.parts    = parts;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String message, RecognitionException exception) {
            if (!(offendingSymbol instanceof Token refused) || !(recognizer instanceof Parser parser)) {
                return;
            }
            refusals++;
            // A token that takes no space was added to set right a mistake that is
            // reported already. Refusing it is the same mistake seen again.
            if (!isAdded(refused)) {
                val problem = SyntaxErrorClassifier.classify(new Refusal(read, indexOf(refused, read.tokens()),
                        new Expected(withoutEnd(strategy.expectedAt(parser, refused, exception))),
                        strategy.contextAt(parser, refused)));
                problems.add(problem);
                if (parts.takes(problem)) {
                    firstOfTheirParts++;
                }
            }
            if (firstOfTheirParts > MAX_REPORTED_PROBLEMS || refusals > MAX_REFUSALS) {
                gaveUp = true;
                throw new ParseCancellationException();
            }
        }

        private static boolean isAdded(Token token) {
            return token.getType() != Token.EOF && token.getStopIndex() < token.getStartIndex();
        }

        /**
         * @return the index of a token among the tokens the parser read, or their
         * number for the end of the document
         */
        private static int indexOf(Token refused, List<? extends Token> read) {
            if (refused.getType() == Token.EOF) {
                return read.size();
            }
            var low  = 0;
            var high = read.size();
            while (low < high) {
                val middle = (low + high) >>> 1;
                if (read.get(middle).getTokenIndex() < refused.getTokenIndex()) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }

        /**
         * The end of the document is not named among the tokens that can stand at a
         * place.
         */
        private static IntervalSet withoutEnd(IntervalSet expected) {
            val tokens = new IntervalSet().addAll(expected);
            tokens.remove(Token.EOF);
            tokens.remove(Token.EPSILON);
            return (IntervalSet) tokens;
        }

        List<Diagnostic> problems() {
            return problems;
        }

        boolean gaveUp() {
            return gaveUp;
        }
    }

}
