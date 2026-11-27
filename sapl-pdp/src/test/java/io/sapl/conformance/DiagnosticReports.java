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

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.FurtherRegion;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The reports of the corpora "diagnostics" and "several-mistakes": what this
 * engine reports for a document, written in the format of the corpus.
 * The comparison omits messages and labels.
 */
@UtilityClass
public class DiagnosticReports {

    /**
     * The report that says that a document has no problem.
     */
    public static final String ACCEPTED = "accepted";

    /**
     * What separates the problems of a report: an empty line.
     */
    public static final String BETWEEN_PROBLEMS = "\n\n";

    /**
     * A block of a report that stands for a run of problems of one class, one
     * after the other: {@code <100 times assignment-instead-of-comparison>}. The
     * groups "count" and "identifier" hold the number and the class.
     */
    public static final Pattern RUN_OF_PROBLEMS = Pattern.compile("<(?<count>\\d+) times (?<identifier>[a-z0-9-]+)>");

    private static final String TOO_MANY_PROBLEMS = "too-many-problems";

    private static final String LINE_FEED         = "\n";
    private static final String RUN               = "<%d times %s>";
    private static final String WRITTEN_FURTHER   = "also %s %s";
    private static final String WRITTEN_PROBLEM   = "%s %s";
    private static final String WRITTEN_QUICK_FIX = "fix %s \"%s\"";
    private static final String WRITTEN_REGION    = "%d:%d-%d:%d";

    /**
     * @param document a SAPL document
     * @return what this engine reports for the document, in the format of the
     * corpus
     */
    public static String reportOf(String document) {
        val parsed = DocumentCompiler.parseDocument(document);
        return parsed.isInvalid() ? writtenReport(parsed.diagnostics()) : ACCEPTED;
    }

    /**
     * @param diagnostics the problems of a document
     * @return the problems in the format of the corpus
     */
    public static String writtenReport(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(DiagnosticReports::written).collect(Collectors.joining(BETWEEN_PROBLEMS))
                .stripTrailing();
    }

    /**
     * @param reportCase a case of the corpus "diagnostics" or "several-mistakes"
     * @return the case with the report of this engine, written with runs where
     * the case writes them
     */
    public static Case withReport(Case reportCase) {
        return reportCase.withExpectation(withRunsAsStated(reportOf(reportCase.document()), reportCase.expectation()));
    }

    /**
     * The report with its problems written as runs wherever the stated report
     * does so. A run of the stated report takes the problems of its class that
     * follow each other, up to its number, so that a report with fewer of them
     * differs in the number.
     *
     * @param reported the report of this engine
     * @param stated the report the corpus states
     * @return the report of this engine with the runs of the stated report
     */
    public static String withRunsAsStated(String reported, String stated) {
        val reportedBlocks = new ArrayDeque<>(List.of(reported.split(BETWEEN_PROBLEMS)));
        val blocks         = new ArrayList<String>();
        for (val statedBlock : stated.split(BETWEEN_PROBLEMS)) {
            val run = RUN_OF_PROBLEMS.matcher(statedBlock);
            if (run.matches()) {
                val count      = Integer.parseInt(run.group("count"));
                val identifier = run.group("identifier");
                var found      = 0;
                while (found < count && !reportedBlocks.isEmpty() && isOfClass(reportedBlocks.getFirst(), identifier)) {
                    reportedBlocks.removeFirst();
                    found++;
                }
                blocks.add(RUN.formatted(found, identifier));
            } else if (!reportedBlocks.isEmpty()) {
                blocks.add(reportedBlocks.removeFirst());
            }
        }
        blocks.addAll(reportedBlocks);
        return String.join(BETWEEN_PROBLEMS, blocks);
    }

    /**
     * @param reported the report of this engine
     * @param stated the report the corpus states
     * @param severalMistakes whether additional problems are allowed and only classes and regions are compared
     * @return whether the report satisfies the corpus, including its problem cap
     */
    public static boolean agrees(String reported, String stated, boolean severalMistakes) {
        if (!severalMistakes) {
            return diagnosticContract(reported).equals(diagnosticContract(stated));
        }
        if (ACCEPTED.equals(reported) || ACCEPTED.equals(stated)) {
            return reported.equals(stated);
        }
        val remaining = new ArrayList<>(List.of(reported.split(BETWEEN_PROBLEMS)).stream()
                .map(block -> block.lines().findFirst().orElse("")).toList());
        val notices   = remaining.stream().filter(line -> isOfClass(line, TOO_MANY_PROBLEMS)).count();
        val problems  = remaining.size() - notices;
        if (problems > 100 || notices > 1 || notices > 0 && problems != 100) {
            return false;
        }
        for (val block : stated.split(BETWEEN_PROBLEMS)) {
            val run = RUN_OF_PROBLEMS.matcher(block);
            if (run.matches()) {
                val count      = Integer.parseInt(run.group("count"));
                val identifier = run.group("identifier");
                for (var index = 0; index < count; index++) {
                    val found = remaining.stream().filter(line -> isOfClass(line, identifier)).findFirst();
                    if (found.isEmpty()) {
                        return false;
                    }
                    remaining.remove(found.get());
                }
            } else if (!remaining.remove(block.lines().findFirst().orElse(""))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> diagnosticContract(String report) {
        return List.of(report.split(BETWEEN_PROBLEMS)).stream().map(block -> {
            val lines = block.lines().toList();
            val parts = new ArrayList<String>();
            parts.add(lines.getFirst());
            parts.addAll(lines.stream().skip(3).filter(line -> line.startsWith("also ") || line.startsWith("fix "))
                    .map(line -> line.startsWith("also ") ? line.substring(0, line.indexOf(' ', 5)) : line).sorted()
                    .toList());
            return String.join(LINE_FEED, parts);
        }).sorted().toList();
    }

    private static boolean isOfClass(String block, String identifier) {
        return block.startsWith(identifier + " ");
    }

    private static String written(Diagnostic diagnostic) {
        val lines = new ArrayList<String>();
        lines.add(WRITTEN_PROBLEM.formatted(diagnostic.identifier(), written(diagnostic.region())));
        lines.add(diagnostic.shortForm());
        lines.add(diagnostic.explanation());
        for (val furtherRegion : diagnostic.furtherRegions()) {
            lines.add(written(furtherRegion));
        }
        val quickFix = diagnostic.quickFix();
        if (quickFix != null) {
            lines.add(WRITTEN_QUICK_FIX.formatted(written(quickFix.region()), quickFix.replacement()));
        }
        return String.join(LINE_FEED, lines);
    }

    private static String written(FurtherRegion furtherRegion) {
        return WRITTEN_FURTHER.formatted(written(furtherRegion.region()), furtherRegion.label().text());
    }

    private static String written(Region region) {
        return WRITTEN_REGION.formatted(region.line(), region.column(), region.endLine(), region.endColumn());
    }
}
