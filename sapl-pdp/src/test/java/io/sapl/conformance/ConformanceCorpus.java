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
package io.sapl.conformance;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Reads the conformance corpus that all implementations of SAPL share. The
 * corpus is not part of this repository. Its README.md describes the format of
 * the case files, and this reader follows it.
 * <p>
 * A file or a line that only resembles the format is refused. If it were
 * skipped, its cases would be missing and no test would fail.
 */
@UtilityClass
public class ConformanceCorpus {

    /**
     * The mark that starts a case. The rest of its line is the heading.
     */
    public static final String HEADING = "=== ";

    /**
     * The line between the document of a case and what is expected for it.
     */
    public static final String SEPARATOR = "---";

    /**
     * The system property that names the directory of the corpus.
     */
    private static final String DIRECTORY_PROPERTY = "sapl.conformance.directory";

    /**
     * The directory of the corpus if the system property does not name one: the
     * corpus of the sapl-rust repository next to this one, seen from the module
     * sapl-pdp, in which the tests run.
     */
    private static final String DEFAULT_DIRECTORY = "../../sapl-rust/conformance";

    private static final String ERROR_CARRIAGE_RETURN       = "The case file %s holds a carriage return. Lines end with a line feed, and a carriage return in a document is written as <U+000D>.";
    private static final String ERROR_DIRECTORY_MISSING     = "The conformance corpus was not found at '%s'. The system property '%s' names the directory that holds it.";
    private static final String ERROR_HEADING_WITHOUT_SPACE = "Line %2$d of the case file %1$s starts with '===' and does not go on with a space. Such a line is a heading. In a document its first character is written as <U+003D>.";
    private static final String ERROR_NO_CASE               = "The directory '%s' of the conformance corpus holds no case.";
    private static final String ERROR_NO_CASE_FILE          = "The file %s is no case file. A directory of the corpus holds only files with the extension 'cases'.";
    private static final String ERROR_NO_CHARACTER          = "The placeholder <%2$s> of the case at %1$s names no character.";
    private static final String ERROR_UNREADABLE            = "The conformance corpus at '%s' cannot be read.";

    private static final String CASE_FILE_EXTENSION = ".cases";
    private static final String CODE_POINT          = "U+";
    private static final String HEADING_MARK        = "===";
    private static final String TIMES               = " times ";

    private static final int HEXADECIMAL = 16;

    /**
     * One case of the corpus.
     *
     * @param origin the file and the line where the case begins
     * @param heading what the case shows. In the parser corpus the verdict
     * precedes it.
     * @param documentAsWritten the document as the file writes it, with its
     * placeholders
     * @param document the SAPL document, with the placeholders replaced by the
     * text they stand for
     * @param expectation what must be reported for the document. Empty in the
     * parser corpus.
     */
    public record Case(String origin, String heading, String documentAsWritten, String document, String expectation) {

        /**
         * @param newHeading the heading that replaces the one of this case
         * @return this case with the heading
         */
        public Case withHeading(String newHeading) {
            return new Case(origin, newHeading, documentAsWritten, document, expectation);
        }

        /**
         * @param newExpectation the expectation that replaces the one of this case
         * @return this case with the expectation
         */
        public Case withExpectation(String newExpectation) {
            return new Case(origin, heading, documentAsWritten, document, newExpectation);
        }

        /**
         * @return where the case begins and what it shows, which names it in a
         * report of the tests
         */
        @Override
        public String toString() {
            return origin + " " + heading;
        }
    }

    /**
     * One case file of the corpus.
     *
     * @param path where the file is
     * @param introduction the lines before the first case, which say what the
     * file is about
     * @param cases the cases of the file, in their order
     */
    public record CaseFile(Path path, List<String> introduction, List<Case> cases) {}

    /**
     * Reads the cases of all files of a corpus, in the order of the file names.
     *
     * @param corpus the name of the corpus, which is a directory of the
     * conformance corpus, for example "parser"
     * @return the cases
     * @throws IllegalStateException if the directory is missing, holds no case,
     * or holds a file that does not follow the format
     */
    public static List<Case> casesOf(String corpus) {
        return caseFilesOf(corpus).stream().flatMap(file -> file.cases().stream()).toList();
    }

    /**
     * Reads all files of a corpus, in the order of their names.
     *
     * @param corpus the name of the corpus, which is a directory of the
     * conformance corpus, for example "parser"
     * @return the case files
     * @throws IllegalStateException if the directory is missing, holds no case,
     * or holds a file that does not follow the format
     */
    public static List<CaseFile> caseFilesOf(String corpus) {
        val directory = directoryOf(corpus);
        val files     = filesIn(directory).stream().map(file -> caseFileOf(corpus + "/" + file.getFileName(), file))
                .toList();
        if (files.stream().allMatch(file -> file.cases().isEmpty())) {
            throw new IllegalStateException(ERROR_NO_CASE.formatted(directory));
        }
        return files;
    }

    /**
     * @param name the name of a file or directory of the conformance corpus, for
     * example "function-catalogue.json"
     * @return where it is, whether it exists or not
     */
    public static Path pathOf(String name) {
        val property   = System.getProperty(DIRECTORY_PROPERTY);
        val configured = property == null || property.isBlank() ? DEFAULT_DIRECTORY : property;
        return Path.of(configured, name).toAbsolutePath().normalize();
    }

    private static Path directoryOf(String corpus) {
        val directory = pathOf(corpus);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(ERROR_DIRECTORY_MISSING.formatted(directory, DIRECTORY_PROPERTY));
        }
        return directory;
    }

    private static List<Path> filesIn(Path directory) {
        try (val files = Files.list(directory)) {
            return files.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_UNREADABLE.formatted(directory), e);
        }
    }

    private static CaseFile caseFileOf(String name, Path file) {
        if (!file.toString().endsWith(CASE_FILE_EXTENSION)) {
            throw new IllegalStateException(ERROR_NO_CASE_FILE.formatted(name));
        }
        val lines = linesOf(name, file);
        // A case reaches from its heading to the next one. The lines before the first
        // heading say what the file is about.
        val headings     = IntStream.range(0, lines.size()).filter(index -> lines.get(index).startsWith(HEADING))
                .boxed().toList();
        val cases        = IntStream.range(0, headings.size()).mapToObj(number -> {
                             val heading = headings.get(number);
                             val next    = number + 1 < headings.size() ? headings.get(number + 1) : lines.size();
                             return caseOf(name + ":" + (heading + 1), lines.get(heading).substring(HEADING.length()),
                                     lines.subList(heading + 1, next));
                         })
                .toList();
        val introduction = lines.subList(0, headings.isEmpty() ? lines.size() : headings.getFirst());
        return new CaseFile(file, introduction, cases);
    }

    private static List<String> linesOf(String name, Path file) {
        final String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_UNREADABLE.formatted(file), e);
        }
        if (text.indexOf('\r') >= 0) {
            throw new IllegalStateException(ERROR_CARRIAGE_RETURN.formatted(name));
        }
        val lines = text.lines().toList();
        for (var index = 0; index < lines.size(); index++) {
            val line = lines.get(index);
            if (line.startsWith(HEADING_MARK) && !line.startsWith(HEADING)) {
                throw new IllegalStateException(ERROR_HEADING_WITHOUT_SPACE.formatted(name, index + 1));
            }
        }
        return lines;
    }

    private static Case caseOf(String origin, String heading, List<String> body) {
        val separator   = body.indexOf(SEPARATOR);
        val document    = separator < 0 ? body : body.subList(0, separator);
        val expectation = separator < 0 ? List.<String>of() : body.subList(separator + 1, body.size());
        val written     = joined(document);
        return new Case(origin, heading, written, withPlaceholdersReplaced(written, origin), joined(expectation));
    }

    /**
     * The lines as one text, without the empty lines at their end. Those only set
     * a case apart from the next one.
     */
    private static String joined(List<String> lines) {
        var length = lines.size();
        while (length > 0 && lines.get(length - 1).isEmpty()) {
            length--;
        }
        return String.join("\n", lines.subList(0, length));
    }

    /**
     * The document as it is parsed: each placeholder in angle brackets is replaced
     * by the text it stands for. Every other angle bracket stands for itself.
     *
     * @param written a text with placeholders, as a case file writes it
     * @param origin the file and the line of the case, which names it if a
     * placeholder is malformed
     * @return the text with the placeholders replaced
     */
    public static String withPlaceholdersReplaced(String written, String origin) {
        val document = new StringBuilder();
        var rest     = written;
        var bracket  = rest.indexOf('<');
        while (bracket >= 0) {
            document.append(rest, 0, bracket);
            val fromBracket = rest.substring(bracket + 1);
            val end         = fromBracket.indexOf('>');
            val text        = end < 0 ? Optional.<String>empty()
                    : textOfPlaceholder(fromBracket.substring(0, end), origin);
            if (text.isPresent()) {
                document.append(text.get());
                rest = fromBracket.substring(end + 1);
            } else {
                document.append('<');
                rest = fromBracket;
            }
            bracket = rest.indexOf('<');
        }
        return document.append(rest).toString();
    }

    /**
     * The text that the inside of a placeholder stands for: "U+202E" for the
     * character with that code point, "33 times [" for 33 opening brackets. Empty
     * if it is no placeholder but SAPL.
     */
    private static Optional<String> textOfPlaceholder(String inside, String origin) {
        if (inside.startsWith(CODE_POINT)) {
            return Optional.of(characterOf(inside, origin));
        }
        val times = inside.indexOf(TIMES);
        if (times < 0) {
            return Optional.empty();
        }
        val count = inside.substring(0, times);
        if (count.isEmpty() || !count.chars().allMatch(ConformanceCorpus::isDecimalDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(inside.substring(times + TIMES.length()).repeat(Integer.parseInt(count)));
        } catch (NumberFormatException e) {
            // A number that is too large to repeat a text that often is no number of
            // a repetition, so the text is SAPL.
            return Optional.empty();
        }
    }

    private static boolean isDecimalDigit(int character) {
        return character >= '0' && character <= '9';
    }

    /**
     * What starts with "U+" and names no character is a mistake in the case. Left
     * alone, it would be parsed as SAPL, and a document that is refused for that
     * would pass as invalid.
     */
    private static String characterOf(String placeholder, String origin) {
        try {
            val codePoint   = Integer.parseInt(placeholder.substring(CODE_POINT.length()), HEXADECIMAL);
            val isSurrogate = codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
            if (Character.isValidCodePoint(codePoint) && !isSurrogate) {
                return Character.toString(codePoint);
            }
            throw new IllegalStateException(ERROR_NO_CHARACTER.formatted(origin, placeholder));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(ERROR_NO_CHARACTER.formatted(origin, placeholder), e);
        }
    }

}
