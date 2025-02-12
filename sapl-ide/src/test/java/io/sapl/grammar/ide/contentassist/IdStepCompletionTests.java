/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;

class IdStepCompletionTests extends CompletionTests {

    @Test
    void testCompletion_AttributeStepCReturnsTemperatureFunctions() {
        final var document = "policy \"test\" permit where subject.<t§";
        final var expected = List.of("temperature.atLocation>", "temperature.atTime>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_AttributeStepWithPrefixReturnsMatchingTemperatureFunction() {
        final var document = "policy \"test\" permit where subject.<temperature.atL§";
        final var expected = List.of("atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_AttributeStepWithNoMatchingPrefixReturnsNoMatchingFunction() {
        final var document = "policy \"test\" permit where subject.<foo§";
        assertProposalsEmpty(document);

    }

    @Test
    void testCompletion_HeadEmptyAttributeStepReturnsTemperatureFunctions() {
        final var document = "policy \"test\" permit where subject.|<t§";
        final var expected = List.of("temperature.atLocation>", "temperature.atTime>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_HeadAttributeStepWithPrefixReturnsMatchingTemperatureFunction() {
        final var document = "policy \"test\" permit where subject.|<temperature.atT§";
        final var expected = List.of("atTime>");
        assertProposalsContain(document, expected);

    }

    @Test
    void testCompletion_HeadAttributeStepWithNonReservedPrefixReturnsMatchingFunction() {
        final var document = "policy \"test\" permit where foo.|<temperature.atT§";
        final var expected = List.of("atTime>");
        assertProposalsContain(document, expected);

    }
}
