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

import static io.sapl.spring.pep.constraints.EnforcementPlanAssert.assertThatPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.providers.AccessConstraintViolationException;
import io.sapl.spring.pep.constraints.EnforcementPlanner.SubstitutionReason;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EnforcementPlannerTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final SignalType DECISION_SIGNAL_TYPE = new SignalType.ValueSignalType<>(Signal.DecisionSignal.class,
            AuthorizationDecision.class);
    private static final SignalType INPUT_SIGNAL_TYPE    = new SignalType.ValueSignalType<>(Signal.InputSignal.class,
            org.aopalliance.intercept.MethodInvocation.class);
    @SuppressWarnings("unchecked")
    private static final SignalType OUTPUT_STRING_TYPE   = new SignalType.ValueSignalType<>(
            (Class<? extends Signal.ValueSignal<String>>) Signal.OutputSignal.class, String.class);
    private static final SignalType CANCEL_SIGNAL_TYPE   = new SignalType.VoidSignalType(Signal.CancelSignal.class);

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DECISION_SIGNAL_TYPE, INPUT_SIGNAL_TYPE,
            OUTPUT_STRING_TYPE, CANCEL_SIGNAL_TYPE);

    @Mock
    ConstraintHandlerProvider provider;

    private static AuthorizationDecision decision(String json) {
        return MAPPER.readValue(json, AuthorizationDecision.class);
    }

    private static Value value(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    private static Value id(String id) {
        return value("{\"id\":\"" + id + "\"}");
    }

    private EnforcementPlanner plannerWith(ConstraintHandlerProvider... providers) {
        return new EnforcementPlanner(List.of(providers));
    }

    private static ScopedConstraintHandler scoped(ConstraintHandler<?> handler, SignalType signalType, int priority) {
        return new ScopedConstraintHandler(handler, signalType, priority);
    }

    private static ConstraintHandler.Runner runner() {
        return () -> {};
    }

    private static ConstraintHandler.Mapper<Object> mapper() {
        return v -> v;
    }

    private static ConstraintHandler.Consumer<Object> consumer() {
        return v -> {};
    }

    @Nested
    @DisplayName("Invariant 6: Coverage")
    class CoverageInvariant {

        @Test
        @DisplayName("empty decision yields empty plan")
        void givenEmptyDecisionThenPlanIsEmpty() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [],
                      "advice": [],
                      "resource": null
                    }
                    """);
            val plan     = plannerWith().plan(decision, SUPPORTED_SIGNALS);
            assertThat(plan.entries()).isEmpty();
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("each obligation and advice produces exactly one entry")
        void givenMixedConstraintsThenOneEntryPerConstraint() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [
                        {"id": "o1"},
                        {"id": "o2"}
                      ],
                      "advice": [
                        {"id": "a1"}
                      ],
                      "resource": null
                    }
                    """);
            lenient().when(provider.getConstraintHandler(id("o1")))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            lenient().when(provider.getConstraintHandler(id("o2")))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            lenient().when(provider.getConstraintHandler(id("a1")))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entries().values().stream().mapToInt(List::size).sum()).isEqualTo(3);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Invariant 1: Ordering")
    class OrderingInvariant {

        @Test
        @DisplayName("entries at the same signal are sorted by ascending priority")
        void givenMixedPrioritiesThenSortedAscending() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [
                        {"id": "a"},
                        {"id": "b"},
                        {"id": "c"}
                      ],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("a")))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 50)));
            when(provider.getConstraintHandler(id("b")))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 10)));
            when(provider.getConstraintHandler(id("c")))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 30)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(CANCEL_SIGNAL_TYPE)).extracting(EnforcementPlanEntry::priority)
                    .containsExactly(10, 30, 50);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("at equal priority Runner sorts before Mapper before Consumer")
        void givenSamePriorityThenRunnerBeforeMapperBeforeConsumer() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [
                        {"id": "m"},
                        {"id": "c"},
                        {"id": "r"}
                      ],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("r")))
                    .thenReturn(Optional.of(scoped(runner(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("c")))
                    .thenReturn(Optional.of(scoped(consumer(), OUTPUT_STRING_TYPE, 5)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE))
                    .extracting(entry -> entry.handler().getClass().getInterfaces()[0].getSimpleName())
                    .containsExactly("Runner", "Mapper", "Consumer");
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Invariant 2: Type-signal admissibility")
    class TypeSignalAdmissibilityInvariant {

        @Test
        @DisplayName("mapper at void signal is replaced by failure substitute")
        void givenMapperAtVoidSignalThenSubstituteAtDecision() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [{"id": "bad"}],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("bad")))
                    .thenReturn(Optional.of(scoped(mapper(), CANCEL_SIGNAL_TYPE, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(CANCEL_SIGNAL_TYPE)).isEmpty();
            assertThat(plan.entriesFor(DECISION_SIGNAL_TYPE)).hasSize(1).first()
                    .extracting(entry -> entry.handler() instanceof ConstraintHandler.Runner).isEqualTo(true);
            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.INADMISSIBLE);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("handler at signal the PEP does not fire is replaced by INADMISSIBLE substitute")
        void givenHandlerAtUnsupportedSignalThenInadmissibleSubstitute() {
            val unsupportedSignal = new SignalType.VoidSignalType(Signal.CompleteSignal.class);
            val decision          = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [{"id": "x"}],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("x")))
                    .thenReturn(Optional.of(scoped(runner(), unsupportedSignal, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(unsupportedSignal)).isEmpty();
            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.INADMISSIBLE);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("runner at void signal is admissible")
        void givenRunnerAtVoidSignalThenAdmissible() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [{"id": "r"}],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("r")))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(CANCEL_SIGNAL_TYPE)).hasSize(1);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Invariant 3: Mapper tag")
    class MapperTagInvariant {

        @Test
        @DisplayName("mapper tagged as advice is replaced by failure substitute")
        void givenMapperAsAdviceThenSubstitute() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [],
                      "advice": [{"id": "bad-advice-mapper"}],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("bad-advice-mapper")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE)).isEmpty();
            assertThat(plan.entriesFor(DECISION_SIGNAL_TYPE)).hasSize(1);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Invariant 4: Mapper commutativity")
    class MapperCommutativityInvariant {

        @Test
        @DisplayName("two mappers at same priority and signal are both replaced")
        void givenTwoMappersSamePriorityThenBothReplaced() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [
                        {"id": "m1"},
                        {"id": "m2"}
                      ],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("m1")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m2")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE)).hasSize(2)
                    .allSatisfy(entry -> assertThat(entry.handler()).isInstanceOf(ConstraintHandler.Runner.class));
            assertSubstituteFailsWithReason(plan, OUTPUT_STRING_TYPE, SubstitutionReason.NON_COMMUTING_GROUP);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("mappers at distinct priorities are kept")
        void givenMappersAtDistinctPrioritiesThenKept() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [
                        {"id": "m1"},
                        {"id": "m2"}
                      ],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("m1")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m2")))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 10)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE)).hasSize(2)
                    .allSatisfy(entry -> assertThat(entry.handler()).isInstanceOf(ConstraintHandler.Mapper.class));
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Substitution scenarios")
    class SubstitutionScenarios {

        @Test
        @DisplayName("no provider matches: UNRESOLVED substitute at decision signal")
        void givenNoProviderMatchesThenUnresolvedSubstitute() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [{"id": "orphan"}],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("orphan"))).thenReturn(Optional.empty());

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.UNRESOLVED);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("two providers match: AMBIGUOUS substitute at decision signal")
        void givenTwoProvidersMatchThenAmbiguousSubstitute(@Mock ConstraintHandlerProvider second) {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [{"id": "dup"}],
                      "advice": [],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("dup")))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            when(second.getConstraintHandler(id("dup")))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));

            val plan = plannerWith(provider, second).plan(decision, SUPPORTED_SIGNALS);

            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.AMBIGUOUS);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("advice substitute completes silently and does not throw")
        void givenAdviceUnresolvedThenSubstituteCompletes() {
            val decision = decision("""
                    {
                      "decision": "PERMIT",
                      "obligations": [],
                      "advice": [{"id": "a"}],
                      "resource": null
                    }
                    """);
            when(provider.getConstraintHandler(id("a"))).thenReturn(Optional.empty());

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            val substitute = plan.entriesFor(DECISION_SIGNAL_TYPE).getFirst();
            assertThat(substitute.constraintType()).isEqualTo(ConstraintType.ADVICE);
            ((ConstraintHandler.Runner) substitute.handler()).run();
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    private static void assertSubstituteFailsWithReason(EnforcementPlan plan, SignalType where,
            SubstitutionReason reason) {
        val substitute = plan.entriesFor(where).stream()
                .filter(entry -> entry.handler() instanceof ConstraintHandler.Runner)
                .filter(entry -> entry.constraintType() == ConstraintType.OBLIGATION).findFirst()
                .orElseThrow(() -> new AssertionError("No obligation runner substitute at " + where));
        val runner     = (ConstraintHandler.Runner) substitute.handler();
        assertThatThrownBy(runner::run).isInstanceOf(AccessConstraintViolationException.class)
                .hasMessageContaining(reason.name());
    }
}
