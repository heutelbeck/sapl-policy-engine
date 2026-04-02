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
package io.sapl.benchmark.sapl4;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.stream.Stream;

import io.sapl.api.pdp.CompilerFlags;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IndexingStrategy;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Smoke tests for all benchmark scenarios. Validates that generated policies
 * compile without errors and that evaluating all subscriptions produces no
 * INDETERMINATE decisions. Also reports decision distribution for quick
 * permit-ratio checks without running full JMH benchmarks.
 */
@DisplayName("Scenario compilation and correctness")
class ScenarioCompilationAndCorrectnessTests {

    private static final long[] SEEDS = { 0, 42, 123 };

    @Nested
    @DisplayName("all scenarios compile and evaluate without errors")
    class CompilationAndEvaluation {

        @DisplayName("scenario compiles and produces no INDETERMINATE")
        @ParameterizedTest(name = "{0} seed={1}")
        @MethodSource("io.sapl.benchmark.sapl4.ScenarioCompilationAndCorrectnessTests#scenarioArguments")
        void whenScenarioThenCompilesAndNoIndeterminate(String scenarioName, long seed) {
            val scenario = ScenarioFactory.create(scenarioName, seed);
            val flags    = CompilerFlags.defaults();

            val startCompile = System.nanoTime();
            val components   = scenario.buildPdp(flags);
            val compileTime  = System.nanoTime() - startCompile;
            System.out.printf("Compiled %s-%d in %d ms%n", scenarioName, seed, compileTime / 1_000_000);
            val pdp = components.pdp();

            val counts = new EnumMap<Decision, Integer>(Decision.class);
            for (val d : Decision.values()) {
                counts.put(d, 0);
            }

            for (val sub : scenario.subscriptions()) {
                val decision = pdp.decideOnceBlocking(sub).decision();
                counts.merge(decision, 1, Integer::sum);
            }

            components.dispose();

            assertThat(counts.get(Decision.INDETERMINATE))
                    .as("INDETERMINATE for %s seed=%d (PERMIT=%d DENY=%d NOT_APPLICABLE=%d)", scenarioName, seed,
                            counts.get(Decision.PERMIT), counts.get(Decision.DENY), counts.get(Decision.NOT_APPLICABLE))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("NAIVE and CANONICAL produce identical decisions")
    class IndexConsistency {

        @DisplayName("NAIVE and CANONICAL agree on every subscription")
        @ParameterizedTest(name = "{0} seed={1}")
        @MethodSource("io.sapl.benchmark.sapl4.ScenarioCompilationAndCorrectnessTests#scenarioArguments")
        void whenDifferentIndexThenSameDecisions(String scenarioName, long seed) {
            val scenario = ScenarioFactory.create(scenarioName, seed);
            val naive    = new CompilerFlags(IndexingStrategy.NAIVE, false, 10, 1.5);
            val canon    = new CompilerFlags(IndexingStrategy.CANONICAL, false, 10, 1.5);

            val naiveComponents = scenario.buildPdp(naive);
            val canonComponents = scenario.buildPdp(canon);
            val naivePdp        = naiveComponents.pdp();
            val canonPdp        = canonComponents.pdp();

            val subs = scenario.subscriptions();
            for (int i = 0; i < subs.size(); i++) {
                val       sub           = subs.get(i);
                val       naiveDecision = naivePdp.decideOnceBlocking(sub).decision();
                val       canonDecision = canonPdp.decideOnceBlocking(sub).decision();
                final int idx           = i;
                assertThat(canonDecision).as("subscription[%d] %s", idx, sub).isEqualTo(naiveDecision);
            }

            naiveComponents.dispose();
            canonComponents.dispose();
        }
    }

    static Stream<Arguments> scenarioArguments() {
        val args = new ArrayList<Arguments>();
        for (val name : ScenarioFactory.ALL_SCENARIO_NAMES) {
            if (isSeeded(name)) {
                for (val seed : SEEDS) {
                    args.add(arguments(name, seed));
                }
            } else {
                args.add(arguments(name, 42L));
            }
        }
        return args.stream();
    }

    private static boolean isSeeded(String name) {
        return name.contains("-") && name.matches(".*-\\d+$");
    }

}
