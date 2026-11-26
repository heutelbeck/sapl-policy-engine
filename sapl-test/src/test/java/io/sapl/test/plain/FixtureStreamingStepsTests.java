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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Sequential fixture emissions")
class FixtureStreamingStepsTests {

    private record Script(
            String name,
            String condition,
            String given,
            String initial,
            String emitted,
            String expected) {}

    static Stream<Arguments> streamingSteps() {
        return Stream.of(false, true)
                .flatMap(mode -> Stream.of(
                        arguments(new Script("new seeded reader", "<desk.a> == 1 && standard.length(<desk.k>) == 2", """
                                - attribute "a" <desk.a> emits 0
                                - attribute "k" <desk.k> emits "z"
                                """, "not-applicable", """
                                - attribute "a" emits 1
                                - attribute "k" emits "zz"
                                """, "- not-applicable - permit"), mode),
                        arguments(new Script("chain of seeded readers",
                                "<desk.a> == 1 && <desk.b> == 1 && standard.length(<desk.k>) == 2", """
                                        - attribute "a" <desk.a> emits 0
                                        - attribute "b" <desk.b> emits 1
                                        - attribute "k" <desk.k> emits "z"
                                        """, "not-applicable", """
                                        - attribute "a" emits 1
                                        - attribute "k" emits "zz"
                                        """, "- not-applicable - permit"), mode),
                        arguments(new Script("unseeded group with a retained sibling",
                                "<desk.a> == 0 || [<desk.b>,<desk.c>] == [1,0]", """
                                        - attribute "a" <desk.a> emits 0
                                        - attribute "b" <desk.b> emits 1
                                        - attribute "c" <desk.c>
                                        """, "permit", """
                                        - attribute "a" emits 1
                                        - attribute "c" emits 2
                                        - attribute "c" emits 0
                                        """, "- not-applicable - permit"), mode)));
    }

    @ParameterizedTest(name = "{index}: lowLatency={1}")
    @MethodSource("streamingSteps")
    @DisplayName("each then emission completes its available rounds before the next")
    void whenThenEmissionsReachSeededMocksThenIntermediateRoundsAreRetained(Script script, boolean lowLatency) {
        val policy = "policy \"p\" permit " + script.condition() + ";";
        val source = """
                requirement "sequential emissions" {
                    given - document "p" - low-latency-mode ${mode}
                        ${given}
                    scenario "each authored value settles"
                        when "reader" attempts null on null
                        expect ${initial}
                        then ${emitted}
                        expect ${expected};
                }
                """.replace("${mode}", Boolean.toString(lowLatency)).replace("${given}", script.given())
                .replace("${initial}", script.initial()).replace("${emitted}", script.emitted())
                .replace("${expected}", script.expected());
        val config = TestConfiguration.builder().withVerificationTimeout(Duration.ofSeconds(2))
                .withSaplDocument(SaplDocument.of("p", policy))
                .withSaplTestDocument(SaplTestDocument.of("steps", source)).build();

        val result = new PlainTestAdapter().execute(config);

        assertThat(result.scenarioResults()).as(script.name()).singleElement()
                .satisfies(scenario -> assertThat(scenario.status()).isEqualTo(TestStatus.PASSED));
    }
}
