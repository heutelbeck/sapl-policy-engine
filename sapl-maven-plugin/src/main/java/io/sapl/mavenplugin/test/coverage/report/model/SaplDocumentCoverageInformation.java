/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage.report.model;

import java.nio.file.Path;

import lombok.Getter;

/**
 * Generic model with coverage information of type {@link LineCoveredValue} for
 * each line of a SAPL document *
 */
public class SaplDocumentCoverageInformation {

    /**
     * Holding detailed information for every line
     */
    private final SaplDocumentLineCoverageInformation[] lines;

    /**
     * Path to the SAPL policy on the filesystem
     */
    @Getter
    private final Path pathToDocument;

    /**
     * initialize a {@link SaplDocumentCoverageInformation} with
     *
     * @param pathToDocument path to the document on the filesystem
     * @param lineCount      line count
     */
    public SaplDocumentCoverageInformation(Path pathToDocument, int lineCount) {
        this.pathToDocument = pathToDocument;
        this.lines          = new SaplDocumentLineCoverageInformation[lineCount];
        for (int i = 0; i < lineCount; i++) {
            lines[i] = new SaplDocumentLineCoverageInformation(i + 1);
        }
    }

    /**
     * mark the line as one of {@link LineCoveredValue}
     *
     * @param lineNumber      (starting with 1)
     * @param value           {@link LineCoveredValue}
     * @param coveredBranches covered branches of this line
     * @param branchesToCover branches to cover of this line
     */
    public void markLine(int lineNumber, LineCoveredValue value, int coveredBranches, int branchesToCover) {
        lines[lineNumber - 1].setCoveredValue(value, coveredBranches, branchesToCover);
    }

    /**
     * Get line coverage information for a line in this document
     *
     * @param lineNumber (starting with 1)
     * @return the {@link SaplDocumentLineCoverageInformation} for this line
     */
    public SaplDocumentLineCoverageInformation getLine(int lineNumber) {
        return this.lines[lineNumber - 1];
    }

    public int getLineCount() {
        return this.lines.length;
    }

}
