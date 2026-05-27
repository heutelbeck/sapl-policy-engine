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
package io.sapl.spring.pep.streaming;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.spring.pep.streaming.MealyMachine.Step;

/**
 * Cell-level content tests for {@link MealyMachine#step(State, Event)}.
 * <p>
 * Each row of {@code /mealy-table.csv} is one cell of the transition
 * function: a {@code (source, event, outcome) -> (next, emissions)}
 * record. This class is one parameterised test over the table; the CSV
 * is the executable spec, the test is the witness that the implementation
 * renders the spec faithfully.
 * <p>
 * This file performs content checks only. Semantic-subset claims
 * (Lean theorems quantified over {@code S × Σ} subsets) live in
 * {@code MealyMachineUniversalInvariantTests}; multi-step path
 * properties live in {@code MealyMachineSequenceInvariantTests}.
 */
class MealyMachineCellTests {

    private static final String TABLE_RESOURCE = "/mealy-table.csv";

    @ParameterizedTest(name = "{0} x {1}({2}) -> {3} : [{4}]")
    @MethodSource("tableRows")
    void cell(String from, String event, String outcome, String expectedTo, String expectedEmissions) {
        var sourceState  = MealyTestSupport.stateByName(from);
        var triggerEvent = MealyTestSupport.eventByName(event, outcome);

        Step step = MealyMachine.step(sourceState, triggerEvent);

        assertThat(step.newState().getClass()).isEqualTo(MealyTestSupport.stateByName(expectedTo).getClass());
        assertThat(emissionKinds(step)).isEqualTo(parseEmissions(expectedEmissions));
    }

    static Stream<Arguments> tableRows() {
        try (var stream = MealyMachineCellTests.class.getResourceAsStream(TABLE_RESOURCE);
                var reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(MealyMachineCellTests::parseRow).toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + TABLE_RESOURCE, e);
        }
    }

    private static Arguments parseRow(String line) {
        var columns = line.split(",", -1);
        if (columns.length != 5) {
            throw new IllegalStateException("Malformed CSV row (expected 5 columns): " + line);
        }
        return arguments(columns[0], columns[1], columns[2], columns[3], columns[4]);
    }

    private static List<String> emissionKinds(Step step) {
        return step.emissions().stream().map(MealyTestSupport::emissionKind).toList();
    }

    private static List<String> parseEmissions(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return List.of(raw.split("\\|"));
    }
}
