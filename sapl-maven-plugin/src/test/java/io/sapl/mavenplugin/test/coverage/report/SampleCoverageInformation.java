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
package io.sapl.mavenplugin.test.coverage.report;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SampleCoverageInformation {

    private record LineMarking(int lineNumber, LineCoveredValue value, int coveredBranches, int branchesToCover) {
    };

    // @formatter:off
    private static final List<LineMarking> LINE_MARKINGS = List.of(
            new LineMarking( 1, LineCoveredValue.IRRELEVANT, 0, 0),
            new LineMarking( 2, LineCoveredValue.IRRELEVANT, 0, 0),
            new LineMarking( 3, LineCoveredValue.FULLY,      1, 1),
            new LineMarking( 4, LineCoveredValue.FULLY,      1, 1),
            new LineMarking( 5, LineCoveredValue.IRRELEVANT, 0, 0),
            new LineMarking( 6, LineCoveredValue.FULLY,      1, 1),
            new LineMarking( 7, LineCoveredValue.IRRELEVANT, 0, 0),
            new LineMarking( 8, LineCoveredValue.FULLY,      1, 1),
            new LineMarking( 9, LineCoveredValue.IRRELEVANT, 0, 0),
            new LineMarking(10, LineCoveredValue.PARTLY,     1, 2),
            new LineMarking(11, LineCoveredValue.NEVER,      0, 2),
            new LineMarking(12, LineCoveredValue.NEVER,      0, 2)
            );
    // @formatter:on

    public Collection<SaplDocumentCoverageInformation> documents() {
        var document = new SaplDocumentCoverageInformation(Paths.get("src/test/resources/policies/policy_1.sapl"), 12);
        for (var marking : LINE_MARKINGS) {
            document.markLine(marking.lineNumber(), marking.value(), marking.coveredBranches(),
                    marking.branchesToCover());
        }
        return List.of(document);
    }

}
