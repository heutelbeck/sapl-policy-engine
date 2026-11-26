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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import lombok.val;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Unordered diagnostic requirements")
class DiagnosticReportsTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("overlappingRequirements")
    @DisplayName("exact problems and runs use separate reported occurrences")
    void whenProblemRequirementsOverlapThenEachOccurrenceIsUsedOnce(String description, String reported, String stated,
            boolean expected) {
        assertThat(DiagnosticReports.agrees(reported, stated, true)).as(description).isEqualTo(expected);
    }

    static Stream<Arguments> overlappingRequirements() {
        val first  = "assignment-instead-of-comparison 1:26-1:27\nWrong operator '='.\nUse '=='.";
        val second = "assignment-instead-of-comparison 1:45-1:46\nWrong operator '='.\nUse '=='.";
        val third  = "assignment-instead-of-comparison 1:67-1:68\nWrong operator '='.\nUse '=='.";
        val other  = "statement-not-terminated 1:40-1:41\nMissing ';'.\nTerminate this statement.";
        val two    = first + "\n\n" + second;
        val three  = two + "\n\n" + third;
        val runOne = "<1 times assignment-instead-of-comparison>";
        val runTwo = "<2 times assignment-instead-of-comparison>";
        return Stream.of(arguments("run before exact", two, runOne + "\n\n" + first, true),
                arguments("exact before run", two, first + "\n\n" + runOne, true),
                arguments("equal runs", two, runOne + "\n\n" + runOne, true),
                arguments("overlapping counts", three, runOne + "\n\n" + runTwo, true),
                arguments("reversed overlapping counts", three, runTwo + "\n\n" + runOne, true),
                arguments("exact between equal runs", three, runOne + "\n\n" + first + "\n\n" + runOne, true),
                arguments("duplicate exact demand", two, first + "\n\n" + first, false),
                arguments("overlapping count missing", two, runOne + "\n\n" + runTwo, false),
                arguments("exact and run missing", first, first + "\n\n" + runOne, false),
                arguments("exact region missing", two, third, false), arguments("class missing", two, other, false),
                arguments("accepted has no problems", "accepted", first, false),
                arguments("problems are not accepted", first, "accepted", false),
                arguments("both accepted", "accepted", "accepted", true));
    }
}
