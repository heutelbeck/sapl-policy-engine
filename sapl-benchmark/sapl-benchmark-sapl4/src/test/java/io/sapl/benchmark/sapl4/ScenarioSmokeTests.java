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

import java.util.EnumMap;
import java.util.stream.Stream;

import io.sapl.api.pdp.CompilerFlags;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IndexingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Smoke tests for all benchmark scenarios. Validates that generated policies
 * compile without errors and that evaluating all subscriptions produces no
 * INDETERMINATE decisions. Also reports decision distribution for quick
 * permit-ratio checks without running full JMH benchmarks.
 */
@DisplayName("Scenario smoke tests")
class ScenarioSmokeTests {

    private static final long[] SEEDS = { 0, 42, 123 };

    @Nested
    @DisplayName("all scenarios compile and evaluate without errors")
    class CompilationAndEvaluation {

        @ParameterizedTest(name = "{0} seed={1}")
        @MethodSource
        void whenScenarioThenCompilesAndNoIndeterminate(String scenarioName, long seed) {
            var scenario = ScenarioFactory.create(scenarioName, seed);
            var flags    = // CompilerFlags.defaults();
                    new CompilerFlags(IndexingStrategy.NAIVE, false, 10, 1.5);

            var startCompile = System.nanoTime();
            var components   = scenario.buildPdp(flags);
            var compileTime  = System.nanoTime() - startCompile;
            System.out.println("Compiled %s-%d in %d ms".formatted(scenarioName, seed, compileTime / 1_000_000));
            var pdp = components.pdp();

            var counts = new EnumMap<Decision, Integer>(Decision.class);
            for (var d : Decision.values()) {
                counts.put(d, 0);
            }

            for (var sub : scenario.subscriptions()) {
                var decision = pdp.decideOnceBlocking(sub).decision();
                counts.merge(decision, 1, Integer::sum);
            }

            components.dispose();

            assertEquals(0, counts.get(Decision.INDETERMINATE),
                    () -> "INDETERMINATE for %s seed=%d (PERMIT=%d DENY=%d NOT_APPLICABLE=%d)".formatted(scenarioName,
                            seed, counts.get(Decision.PERMIT), counts.get(Decision.DENY),
                            counts.get(Decision.NOT_APPLICABLE)));
        }

        static Stream<Arguments> whenScenarioThenCompilesAndNoIndeterminate() {
            var args = new java.util.ArrayList<Arguments>();
            for (var name : ScenarioFactory.ALL_SCENARIO_NAMES) {
                if (isSeeded(name)) {
                    for (var seed : SEEDS) {
                        args.add(arguments(name, seed));
                    }
                } else {
                    args.add(arguments(name, 42L));
                }
            }
            return args.stream();
        }
    }

    @Nested
    @DisplayName("NAIVE and CANONICAL produce identical decisions")
    class IndexConsistency {

        @ParameterizedTest(name = "{0} seed={1}")
        @MethodSource
        void whenDifferentIndexThenSameDecisions(String scenarioName, long seed) {
            var scenario = ScenarioFactory.create(scenarioName, seed);
            var naive    = new CompilerFlags(IndexingStrategy.NAIVE, false, 10, 1.5);
            var canon    = new CompilerFlags(IndexingStrategy.CANONICAL, false, 10, 1.5);

            var naiveComponents = scenario.buildPdp(naive);
            var canonComponents = scenario.buildPdp(canon);
            var naivePdp        = naiveComponents.pdp();
            var canonPdp        = canonComponents.pdp();

            var subs = scenario.subscriptions();
            for (int i = 0; i < subs.size(); i++) {
                var       sub           = subs.get(i);
                var       naiveDecision = naivePdp.decideOnceBlocking(sub).decision();
                var       canonDecision = canonPdp.decideOnceBlocking(sub).decision();
                final int idx           = i;
                assertEquals(naiveDecision, canonDecision, () -> "subscription[%d] %s".formatted(idx, sub));
            }

            naiveComponents.dispose();
            canonComponents.dispose();
        }

        static Stream<Arguments> whenDifferentIndexThenSameDecisions() {
            var args = new java.util.ArrayList<Arguments>();
            for (var name : ScenarioFactory.ALL_SCENARIO_NAMES) {
                if (isSeeded(name)) {
                    for (var seed : SEEDS) {
                        args.add(arguments(name, seed));
                    }
                } else {
                    args.add(arguments(name, 42L));
                }
            }
            return args.stream();
        }
    }

    private static boolean isSeeded(String name) {
        return name.contains("-") && name.matches(".*-\\d+$");
    }

}
