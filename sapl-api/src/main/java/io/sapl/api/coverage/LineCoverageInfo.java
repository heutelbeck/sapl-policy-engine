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
package io.sapl.api.coverage;

/**
 * Coverage information for a single source line.
 *
 * @param line the 1-based line number
 * @param status the coverage status
 * @param coveredBranches number of covered branches on this line
 * @param totalBranches total number of branches on this line
 */
public record LineCoverageInfo(int line, LineCoverageStatus status, int coveredBranches, int totalBranches) {

    /**
     * Creates coverage info for an irrelevant line (no conditions).
     *
     * @param line the 1-based line number
     * @return coverage info with IRRELEVANT status
     */
    public static LineCoverageInfo irrelevant(int line) {
        return new LineCoverageInfo(line, LineCoverageStatus.IRRELEVANT, 0, 0);
    }

    /**
     * Creates coverage info for a line with conditions.
     *
     * @param line the 1-based line number
     * @param coveredBranches number of covered branches
     * @param totalBranches total number of branches
     * @return coverage info with appropriate status
     */
    public static LineCoverageInfo withBranches(int line, int coveredBranches, int totalBranches) {
        var status = LineCoverageStatus.IRRELEVANT;
        if (totalBranches > 0) {
            if (coveredBranches == 0) {
                status = LineCoverageStatus.NOT_COVERED;
            } else if (coveredBranches >= totalBranches) {
                status = LineCoverageStatus.FULLY_COVERED;
            } else {
                status = LineCoverageStatus.PARTIALLY_COVERED;
            }
        }
        return new LineCoverageInfo(line, status, coveredBranches, totalBranches);
    }

    /**
     * Returns a summary suitable for display (e.g., "2 of 4 branches covered").
     *
     * @return coverage summary string, or null if irrelevant
     */
    public String getSummary() {
        if (status == LineCoverageStatus.IRRELEVANT) {
            return null;
        }
        return "%d of %d branches covered".formatted(coveredBranches, totalBranches);
    }
}
