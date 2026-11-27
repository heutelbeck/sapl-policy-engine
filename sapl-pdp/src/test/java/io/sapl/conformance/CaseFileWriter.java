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

import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.conformance.ConformanceCorpus.CaseFile;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.UnaryOperator;

import static io.sapl.conformance.ConformanceCorpus.HEADING;
import static io.sapl.conformance.ConformanceCorpus.SEPARATOR;

/**
 * Writes the cases of a case file anew in the format of the corpus. The
 * introduction of the file and the documents of its cases stay as they are
 * written, and what the cases state about their documents is replaced.
 */
@UtilityClass
public class CaseFileWriter {

    private static final String ERROR_UNWRITABLE = "The case file '%s' cannot be written.";

    private static final char LINE_FEED = '\n';

    /**
     * Rewrites a case file with each of its cases replaced by what a generator
     * makes of it.
     *
     * @param file the case file as it was read
     * @param generator makes the case to write of each case read. It may change
     * the heading and the expectation of a case.
     * @throws UncheckedIOException if the file cannot be written
     */
    public static void rewrite(CaseFile file, UnaryOperator<Case> generator) {
        val text = written(file.introduction(), file.cases().stream().map(generator).toList());
        try {
            Files.writeString(file.path(), text);
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_UNWRITABLE.formatted(file.path()), e);
        }
    }

    /**
     * The text of a case file: the introduction, then each case with its
     * heading, its document and, if it states anything, a separator and what it
     * states. An empty line sets each case apart from the next one, and the file
     * ends with a single line feed.
     */
    static String written(List<String> introduction, List<Case> cases) {
        val text = new StringBuilder();
        introduction.forEach(line -> text.append(line).append(LINE_FEED));
        for (val writtenCase : cases) {
            text.append(HEADING).append(writtenCase.heading()).append(LINE_FEED);
            if (!writtenCase.documentAsWritten().isEmpty()) {
                text.append(writtenCase.documentAsWritten()).append(LINE_FEED);
            }
            if (!writtenCase.expectation().isEmpty()) {
                text.append(SEPARATOR).append(LINE_FEED).append(writtenCase.expectation()).append(LINE_FEED);
            }
            text.append(LINE_FEED);
        }
        return text.toString().stripTrailing() + LINE_FEED;
    }
}
