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
package io.sapl.spring.pep.constraints;

import static io.sapl.spring.pep.constraints.ConstraintType.ADVICE;
import static io.sapl.spring.pep.constraints.ConstraintType.OBLIGATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.ResolvableType;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Input-plan matching")
class EnforcementPlanMatchingTests {

    private static final ObjectMapper    MAPPER  = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    private static final Value           FIRST   = Value.of("auditInput");
    private static final Value           SECOND  = Value.of("countInput");
    private static final SignalType      OUTPUT  = OutputSignal.typeFor(String.class);
    private static final Set<SignalType> SIGNALS = Set.of(InputSignal.SIGNAL_TYPE, OUTPUT,
            Signal.DecisionSignal.SIGNAL_TYPE);

    @Nested
    @DisplayName("Entry descriptions")
    class EntryDescriptions {

        @MethodSource("entryComparisons")
        @ParameterizedTest(name = "{0}")
        @DisplayName("Match recorded descriptions without executable equality")
        void whenComparingEntriesThenUseRecordedDescription(String scenario, EnforcementPlanEntry<?> left,
                EnforcementPlanEntry<?> right, boolean expected) {
            assertThat(left.matches(right)).as(scenario).isEqualTo(expected);
            assertThat(right.matches(left)).as("symmetric comparison").isEqualTo(expected);
        }

        static Stream<Arguments> entryComparisons() {
            val runner = runner(FIRST, 10, OBLIGATION);
            return Stream.of(arguments("fresh runners", runner, runner(FIRST, 10, OBLIGATION), true),
                    arguments("fresh mappers", mapper(FIRST), mapper(FIRST), true),
                    arguments("fresh consumers", consumer(FIRST), consumer(FIRST), true),
                    arguments("changed constraint", runner, runner(SECOND, 10, OBLIGATION), false),
                    arguments("changed priority", runner, runner(FIRST, 20, OBLIGATION), false),
                    arguments("changed obligation/advice tag", runner, runner(FIRST, 10, ADVICE), false),
                    arguments("runner replaced by mapper", runner, mapper(FIRST), false),
                    arguments("mapper replaced by consumer", mapper(FIRST), consumer(FIRST), false),
                    arguments("consumer replaced by runner", consumer(FIRST), runner, false),
                    arguments("nested object member order", runner(value("""
                            {"type":"audit", "parameters":{"target":"input", "limit":5}}
                            """), 10, OBLIGATION), runner(value("""
                            {"parameters":{"limit":5, "target":"input"}, "type":"audit"}
                            """), 10, OBLIGATION), true),
                    arguments("array order is meaningful", runner(value("[1,2]"), 10, OBLIGATION),
                            runner(value("[2,1]"), 10, OBLIGATION), false),
                    arguments("numeric scale follows Value equality", runner(value("1.0"), 10, OBLIGATION),
                            runner(value("1.00"), 10, OBLIGATION), true),
                    arguments("no schema default normalization", runner(value("""
                            {"type":"limitInput"}
                            """), 10, OBLIGATION), runner(value("""
                            {"type":"limitInput", "limit":100}
                            """), 10, OBLIGATION), false));
        }

        @Test
        @DisplayName("Matching is reflexive and transitive without changing record equality")
        void whenFreshHandlersDescribeSameEntryThenMatchWithoutRecordEquality() {
            val first  = runner(FIRST, 10, OBLIGATION);
            val second = runner(FIRST, 10, OBLIGATION);
            val third  = runner(FIRST, 10, OBLIGATION);

            assertThat(first).isNotEqualTo(second).satisfies(entry -> {
                assertThat(entry.matches(entry)).isTrue();
                assertThat(entry.matches(second)).isTrue();
                assertThat(entry.matches(third)).isTrue();
                assertThat(entry.matches(null)).isFalse();
            });
            assertThat(second.matches(third)).isTrue();
        }
    }

    @Nested
    @DisplayName("Ordered input projection")
    class InputProjection {

        @MethodSource("planComparisons")
        @ParameterizedTest(name = "{0}")
        @DisplayName("Preserve input order and multiplicity while ignoring other signals")
        void whenComparingPlansThenUseOnlyOrderedInputEntries(String scenario, EnforcementPlan left,
                EnforcementPlan right, boolean expected) {
            assertThat(left.matchesInputPlan(right)).as(scenario).isEqualTo(expected);
            assertThat(right.matchesInputPlan(left)).as("symmetric comparison").isEqualTo(expected);
        }

        static Stream<Arguments> planComparisons() {
            val first   = runner(FIRST, 10, OBLIGATION);
            val second  = runner(SECOND, 10, OBLIGATION);
            val missing = new EnforcementPlan(Map.of());
            val input   = inputPlan(first);
            return Stream.of(arguments("missing and empty input buckets", missing, inputPlan(), true),
                    arguments("fresh input entries", input, inputPlan(runner(FIRST, 10, OBLIGATION)), true),
                    arguments("first input handler added", missing, input, false),
                    arguments("input handler appended", input, inputPlan(first, second), false),
                    arguments("last entry changed", inputPlan(first, first), inputPlan(first, second), false),
                    arguments("execution order changed", inputPlan(first, second), inputPlan(second, first), false),
                    arguments("duplicate added", input, inputPlan(first, first), false),
                    arguments("same duplicates retained", inputPlan(first, first),
                            inputPlan(runner(FIRST, 10, OBLIGATION), runner(FIRST, 10, OBLIGATION)), true),
                    arguments(
                            "other signals and output type ignored", input,
                            new EnforcementPlan(ResolvableType.forClass(String.class),
                                    Map.of(InputSignal.SIGNAL_TYPE, List.of(first), OUTPUT, List.of(second),
                                            Signal.DecisionSignal.SIGNAL_TYPE, List.of(second),
                                            Signal.CancelSignal.SIGNAL_TYPE, List.of(second))),
                            true),
                    arguments("output-only plan", missing, new EnforcementPlan(Map.of(OUTPUT, List.of(second))), true));
        }

        @Test
        @DisplayName("A plan matches itself and never matches null")
        void whenComparingWithSelfOrNullThenRespectMatchingContract() {
            val plan = inputPlan(runner(FIRST, 10, OBLIGATION));
            assertThat(plan).satisfies(candidate -> {
                assertThat(candidate.matchesInputPlan(candidate)).isTrue();
                assertThat(candidate.matchesInputPlan(null)).isFalse();
            });
        }

        @Test
        @DisplayName("Comparison never executes input runners, mappers, or consumers")
        void whenComparingInputPlansThenHandlersHaveNoSideEffects() {
            val                                executions = new AtomicInteger();
            ConstraintHandler.Runner           runner     = executions::incrementAndGet;
            ConstraintHandler.Mapper<Object>   mapper     = input -> {
                                                              executions.incrementAndGet();
                                                              return input;
                                                          };
            ConstraintHandler.Consumer<Object> consumer   = ignored -> executions.incrementAndGet();
            val                                first      = inputPlan(
                    new EnforcementPlanEntry<>(runner, 10, OBLIGATION, FIRST),
                    new EnforcementPlanEntry<>(mapper, 10, OBLIGATION, FIRST),
                    new EnforcementPlanEntry<>(consumer, 10, OBLIGATION, FIRST));
            val                                second     = inputPlan(runner(FIRST, 10, OBLIGATION), mapper(FIRST),
                    consumer(FIRST));

            assertThat(first.matchesInputPlan(second)).isTrue();
            assertThat(second.matchesInputPlan(first)).isTrue();
            assertThat(executions).hasValue(0);
        }
    }

    @Nested
    @DisplayName("Real planner integration")
    class PlannerIntegration {

        @CsvSource({ "20, true", "10, false" })
        @ParameterizedTest(name = "second priority {0}, reordered constraints match: {1}")
        @DisplayName("Compare final execution order after priority sorting")
        void whenConstraintsAreReorderedThenCompareSortedPlan(int secondPriority, boolean expected) {
            val executions = new AtomicInteger();
            val planner    = runnerPlanner(Map.of(FIRST, 10, SECOND, secondPriority), executions);
            val first      = planner.plan(permit(List.of(FIRST, SECOND), List.of()), SIGNALS);
            val second     = planner.plan(permit(List.of(SECOND, FIRST), List.of()), SIGNALS);

            assertThat(first.matchesInputPlan(second)).isEqualTo(expected);
            assertThat(executions).hasValue(0);
        }

        @Test
        @DisplayName("Replanning with fresh handlers and changed output constraints preserves the input match")
        void whenOnlyOutputConstraintsChangeThenInputPlansMatch() {
            val executions = new AtomicInteger();
            val planner    = runnerPlanner(Map.of(FIRST, 10), executions);
            val first      = planner.plan(permit(List.of(FIRST, Value.of("redactOutput")), List.of()), SIGNALS);
            val second     = planner.plan(permit(List.of(FIRST, Value.of("filterOutput")), List.of()), SIGNALS);

            assertThat(first.entriesFor(InputSignal.SIGNAL_TYPE))
                    .isNotEqualTo(second.entriesFor(InputSignal.SIGNAL_TYPE));
            assertThat(first.matchesInputPlan(second)).isTrue();
            assertThat(executions).hasValue(0);
        }

        @Test
        @DisplayName("Moving an input constraint from obligation to advice changes the plan")
        void whenInputConstraintTagChangesThenInputPlansDoNotMatch() {
            val executions = new AtomicInteger();
            val planner    = runnerPlanner(Map.of(FIRST, 10), executions);
            val obligation = planner.plan(permit(List.of(FIRST), List.of()), SIGNALS);
            val advice     = planner.plan(permit(List.of(), List.of(FIRST)), SIGNALS);

            assertThat(obligation.matchesInputPlan(advice)).isFalse();
            assertThat(executions).hasValue(0);
        }

        @CsvSource({ "changedInput, originalOutput", "originalInput, changedOutput" })
        @ParameterizedTest(name = "input {0}, output {1}")
        @DisplayName("Multi-signal constraints retain their whole originating value for comparison")
        void whenMultiSignalConstraintChangesThenInputDescriptionsConservativelyDiffer(String input, String output) {
            val                       executions = new AtomicInteger();
            ConstraintHandlerProvider provider   = (constraint, signals) -> {
                                                     ConstraintHandler.Runner handler = executions::incrementAndGet;
                                                     return List.of(
                                                             new ScopedConstraintHandler(handler,
                                                                     InputSignal.SIGNAL_TYPE, 10),
                                                             new ScopedConstraintHandler(handler, OUTPUT, 10));
                                                 };
            val                       planner    = new EnforcementPlanner(List.of(provider), MAPPER);
            val                       original   = Value
                    .ofObject(Map.of("input", Value.of("originalInput"), "output", Value.of("originalOutput")));
            val                       changed    = Value
                    .ofObject(Map.of("input", Value.of(input), "output", Value.of(output)));
            val                       first      = planner.plan(permit(List.of(original), List.of()), SIGNALS);
            val                       second     = planner.plan(permit(List.of(changed), List.of()), SIGNALS);

            assertThat(first.matchesInputPlan(second)).isFalse();
            assertThat(executions).hasValue(0);
        }

        @Test
        @DisplayName("Duplicate mappers produce failure runners that differ from valid input mapping")
        void whenDuplicateMappersConflictThenCompareTheirSyntheticEntries() {
            val                       executions       = new AtomicInteger();
            ConstraintHandlerProvider provider         = (constraint, signals) -> {
                                                           ConstraintHandler.Mapper<Object> handler = input -> {
                                                                                                          executions
                                                                                                                  .incrementAndGet();
                                                                                                          return input;
                                                                                                      };
                                                           return List.of(new ScopedConstraintHandler(handler,
                                                                   InputSignal.SIGNAL_TYPE, 10));
                                                       };
            val                       planner          = new EnforcementPlanner(List.of(provider), MAPPER);
            val                       single           = planner.plan(permit(List.of(FIRST), List.of()), SIGNALS);
            val                       conflict         = planner.plan(permit(List.of(FIRST, FIRST), List.of()),
                    SIGNALS);
            val                       repeatedConflict = planner.plan(permit(List.of(FIRST, FIRST), List.of()),
                    SIGNALS);

            assertThat(conflict.entriesFor(InputSignal.SIGNAL_TYPE)).hasSize(2).allSatisfy(entry -> {
                assertThat(entry.handler()).isInstanceOf(ConstraintHandler.Runner.class);
                assertThat(entry.constraint()).isEqualTo(FIRST);
                assertThat(entry.matches(single.entriesFor(InputSignal.SIGNAL_TYPE).getFirst())).isFalse();
            });
            assertThat(single.matchesInputPlan(conflict)).isFalse();
            assertThat(conflict.matchesInputPlan(repeatedConflict)).isTrue();
            assertThat(executions).hasValue(0);
        }
    }

    private static EnforcementPlan inputPlan(EnforcementPlanEntry<?>... entries) {
        return new EnforcementPlan(Map.of(InputSignal.SIGNAL_TYPE, List.of(entries)));
    }

    private static EnforcementPlanEntry<Void> runner(Value constraint, int priority, ConstraintType tag) {
        val                      executions = new AtomicInteger();
        ConstraintHandler.Runner handler    = executions::incrementAndGet;
        return new EnforcementPlanEntry<>(handler, priority, tag, constraint);
    }

    private static EnforcementPlanEntry<Object> mapper(Value constraint) {
        val                              executions = new AtomicInteger();
        ConstraintHandler.Mapper<Object> handler    = input -> {
                                                        executions.incrementAndGet();
                                                        return input;
                                                    };
        return new EnforcementPlanEntry<>(handler, 10, OBLIGATION, constraint);
    }

    private static EnforcementPlanEntry<Object> consumer(Value constraint) {
        val                                executions = new AtomicInteger();
        ConstraintHandler.Consumer<Object> handler    = ignored -> executions.incrementAndGet();
        return new EnforcementPlanEntry<>(handler, 10, OBLIGATION, constraint);
    }

    private static EnforcementPlanner runnerPlanner(Map<Value, Integer> inputPriorities, AtomicInteger executions) {
        ConstraintHandlerProvider provider = (constraint, signals) -> {
            ConstraintHandler.Runner handler = executions::incrementAndGet;
            val                      signal  = inputPriorities.containsKey(constraint) ? InputSignal.SIGNAL_TYPE
                    : OUTPUT;
            return List.of(new ScopedConstraintHandler(handler, signal, inputPriorities.getOrDefault(constraint, 0)));
        };
        return new EnforcementPlanner(List.of(provider), MAPPER);
    }

    private static AuthorizationDecision permit(List<Value> obligations, List<Value> advice) {
        return new AuthorizationDecision(Decision.PERMIT, new ArrayValue(obligations), new ArrayValue(advice),
                Value.UNDEFINED);
    }

    private static Value value(String json) {
        return MAPPER.readValue(json, Value.class);
    }
}
