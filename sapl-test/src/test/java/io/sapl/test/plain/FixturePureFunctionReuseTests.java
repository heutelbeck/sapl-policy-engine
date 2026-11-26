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
package io.sapl.test.plain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("Executed pure calls in an ordinary SAPLTest fixture")
class FixturePureFunctionReuseTests {
    @Test
    @DisplayName("each scenario executes the repeated pure call once in its own round")
    void whenTheSamePureCallIsReachedTwiceThenItsFixtureRecordsOneActualCallPerRound() {
        val policy        = """
                policy "read" permit time.secondOf(subject) == 24 && time.secondOf(subject) > 0;
                """;
        val test          = """
                requirement "desk" {
                    given
                        - document "read"
                        - function time.secondOf(any) maps to 24
                    scenario "first"
                        when "2026-01-15T10:00:24Z" attempts "enter" on "office"
                        expect permit
                        verify - function time.secondOf(any) is called once;
                    scenario "second"
                        when "2026-01-15T10:00:25Z" attempts "enter" on "office"
                        expect permit
                        verify - function time.secondOf(any) is called once;
                }
                """;
        val configuration = TestConfiguration.builder().withSaplDocument(SaplDocument.of("read", policy))
                .withSaplTestDocument(SaplTestDocument.of("desk", test)).build();

        val result = new PlainTestAdapter().execute(configuration);

        assertThat(result.scenarioResults()).hasSize(2).allSatisfy(scenario -> {
            assertThat(scenario.status()).as(scenario.failureMessage()).isEqualTo(TestStatus.PASSED);
        });
    }
}
