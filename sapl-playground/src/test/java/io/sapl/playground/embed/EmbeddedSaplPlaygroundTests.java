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
package io.sapl.playground.embed;

import static io.sapl.playground.embed.EmbeddedSaplPlayground.isLiveEmission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EmbeddedSaplPlayground stale decision guard")
class EmbeddedSaplPlaygroundTests {

    @Nested
    @DisplayName("Generation guard for decision emissions")
    class GenerationGuard {

        static Stream<Arguments> emissionScenarios() {
            return Stream.of(arguments("current emission while active is displayed", 5L, 5L, true, true),
                    arguments("stale emission from disposed subscription is discarded", 4L, 5L, true, false),
                    arguments("matching emission after stop is discarded", 5L, 5L, false, false),
                    arguments("stale emission after stop is discarded", 4L, 5L, false, false),
                    arguments("future emission is discarded", 6L, 5L, true, false));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("emissionScenarios")
        @DisplayName("only the active subscription's latest emission may repaint the decision editor")
        void whenEmissionGenerationThenLivenessReflectsActiveSubscription(String scenario, long emissionGeneration,
                long activeGeneration, boolean subscriptionActive, boolean expectedLive) {
            assertThat(isLiveEmission(emissionGeneration, activeGeneration, subscriptionActive)).as(scenario)
                    .isEqualTo(expectedLive);
        }
    }
}
