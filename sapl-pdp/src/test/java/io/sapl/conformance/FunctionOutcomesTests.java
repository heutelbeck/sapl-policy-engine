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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.conformance.ConformanceCorpus.Case;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Function corpus outcomes")
class FunctionOutcomesTests {

    @ParameterizedTest(name = "note: {0}")
    @ValueSource(strings = { "ordinary", "UNREPRESENTABLE ordinary", "ordinary UNREPRESENTABLE note" })
    @DisplayName("a literal marker inside JSON does not change value comparison")
    void whenAnObjectContainsTheMarkerAsTextThenItStillAgreesByQuantityAndFields(String note) {
        var document     = """
                array.head
                [{"note":"%s","number":1}]
                """.formatted(note).stripTrailing();
        var expected     = """
                value: {"number":1.0,"note":"%s"}
                """.formatted(note).stripTrailing();
        var functionCase = new Case("ordinary.cases:1", "a note and a number", document, document, expected);

        assertThat(FunctionOutcomes.agrees(functionCase)).isTrue();
    }

    @Test
    @DisplayName("a value with an undefined member is marked as not representable as JSON")
    void whenAnObjectContainsUndefinedThenItsOutcomeStartsWithTheUnrepresentableMarker() {
        var result = ObjectValue.builder().put("note", Value.UNDEFINED).build();

        assertThat(FunctionOutcomes.written(result)).startsWith("value: UNREPRESENTABLE ");
    }
}
