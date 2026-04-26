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
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.EnforcementPlanner.SubstitutionReason;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EnforcementPlannerTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final SignalType DECISION_SIGNAL_TYPE = Signal.DecisionSignal.TYPE;
    private static final SignalType INPUT_SIGNAL_TYPE    = Signal.InputSignal.TYPE;
    private static final SignalType OUTPUT_STRING_TYPE   = Signal.OutputSignal.typeFor(String.class);
    private static final SignalType CANCEL_SIGNAL_TYPE   = new SignalType.VoidSignalType(Signal.CancelSignal.class);

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(DECISION_SIGNAL_TYPE, INPUT_SIGNAL_TYPE,
            OUTPUT_STRING_TYPE, CANCEL_SIGNAL_TYPE);

    @Mock
    ConstraintHandlerProvider provider;

    private static AuthorizationDecision permit(String obligationsJson, String adviceJson) {
        return new AuthorizationDecision(Decision.PERMIT, (ArrayValue) MAPPER.readValue(obligationsJson, Value.class),
                (ArrayValue) MAPPER.readValue(adviceJson, Value.class), Value.UNDEFINED);
    }

    private static AuthorizationDecision permit(String obligationsJson, String adviceJson, String resourceJson) {
        return new AuthorizationDecision(Decision.PERMIT, (ArrayValue) MAPPER.readValue(obligationsJson, Value.class),
                (ArrayValue) MAPPER.readValue(adviceJson, Value.class), MAPPER.readValue(resourceJson, Value.class));
    }

    private static Value value(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    private static Value id(String id) {
        return value("{\"id\":\"" + id + "\"}");
    }

    private EnforcementPlanner plannerWith(ConstraintHandlerProvider... providers) {
        return new EnforcementPlanner(List.of(providers), MAPPER);
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
            val decision = permit("[]", "[]");
            val plan     = plannerWith().plan(decision, SUPPORTED_SIGNALS);
            assertThat(plan.entries()).isEmpty();
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("each obligation and advice produces exactly one entry")
        void givenMixedConstraintsThenOneEntryPerConstraint() {
            val decision = permit("""
                    [{"id": "o1"}, {"id": "o2"}]
                    """, """
                    [{"id": "a1"}]
                    """);
            lenient().when(provider.getConstraintHandler(id("o1"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            lenient().when(provider.getConstraintHandler(id("o2"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            lenient().when(provider.getConstraintHandler(id("a1"), SUPPORTED_SIGNALS))
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
            val decision = permit("""
                    [{"id": "a"}, {"id": "b"}, {"id": "c"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("a"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 50)));
            when(provider.getConstraintHandler(id("b"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 10)));
            when(provider.getConstraintHandler(id("c"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), CANCEL_SIGNAL_TYPE, 30)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(CANCEL_SIGNAL_TYPE)).extracting(EnforcementPlanEntry::priority)
                    .containsExactly(10, 30, 50);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("at equal priority Runner sorts before Mapper before Consumer")
        void givenSamePriorityThenRunnerBeforeMapperBeforeConsumer() {
            val decision = permit("""
                    [{"id": "m"}, {"id": "c"}, {"id": "r"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("r"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("c"), SUPPORTED_SIGNALS))
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
            val decision = permit("""
                    [{"id": "bad"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("bad"), SUPPORTED_SIGNALS))
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
            val decision          = permit("""
                    [{"id": "x"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("x"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), unsupportedSignal, 0)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(unsupportedSignal)).isEmpty();
            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.INADMISSIBLE);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("runner at void signal is admissible")
        void givenRunnerAtVoidSignalThenAdmissible() {
            val decision = permit("""
                    [{"id": "r"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("r"), SUPPORTED_SIGNALS))
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
            val decision = permit("[]", """
                    [{"id": "bad-advice-mapper"}]
                    """);
            when(provider.getConstraintHandler(id("bad-advice-mapper"), SUPPORTED_SIGNALS))
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
            val decision = permit("""
                    [{"id": "m1"}, {"id": "m2"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("m1"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m2"), SUPPORTED_SIGNALS))
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
            val decision = permit("""
                    [{"id": "m1"}, {"id": "m2"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("m1"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, 5)));
            when(provider.getConstraintHandler(id("m2"), SUPPORTED_SIGNALS))
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
            val decision = permit("""
                    [{"id": "orphan"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("orphan"), SUPPORTED_SIGNALS)).thenReturn(Optional.empty());

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.UNRESOLVED);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("two providers match: AMBIGUOUS substitute at decision signal")
        void givenTwoProvidersMatchThenAmbiguousSubstitute(@Mock ConstraintHandlerProvider second) {
            val decision = permit("""
                    [{"id": "dup"}]
                    """, "[]");
            when(provider.getConstraintHandler(id("dup"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));
            when(second.getConstraintHandler(id("dup"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(runner(), DECISION_SIGNAL_TYPE, 0)));

            val plan = plannerWith(provider, second).plan(decision, SUPPORTED_SIGNALS);

            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.AMBIGUOUS);
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("advice substitute completes silently and does not throw")
        void givenAdviceUnresolvedThenSubstituteCompletes() {
            val decision = permit("[]", """
                    [{"id": "a"}]
                    """);
            when(provider.getConstraintHandler(id("a"), SUPPORTED_SIGNALS)).thenReturn(Optional.empty());

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            val substitute = plan.entriesFor(DECISION_SIGNAL_TYPE).getFirst();
            assertThat(substitute.constraintType()).isEqualTo(ConstraintType.ADVICE);
            ((ConstraintHandler.Runner) substitute.handler()).run();
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Implicit resource obligation (SAPL-specific)")
    class ImplicitResourceObligation {

        @Test
        @DisplayName("UNDEFINED resource yields no implicit entry")
        void givenUndefinedResourceThenNoImplicitEntry() {
            val decision = permit("[]", "[]");
            val plan     = plannerWith().plan(decision, SUPPORTED_SIGNALS);
            assertThat(plan.entries()).isEmpty();
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("present resource yields implicit Mapper at OutputSignal at MIN_VALUE")
        void givenPresentResourceThenImplicitMapper() {
            val decision = permit("[]", "[]", "\"replaced\"");
            val plan     = plannerWith().plan(decision, SUPPORTED_SIGNALS);

            val outputEntries = plan.entriesFor(OUTPUT_STRING_TYPE);
            assertThat(outputEntries).hasSize(1).first().satisfies(entry -> {
                assertThat(entry.priority()).isEqualTo(Integer.MIN_VALUE);
                assertThat(entry.constraintType()).isEqualTo(ConstraintType.OBLIGATION);
                assertThat(entry.handler()).isInstanceOf(ConstraintHandler.Mapper.class);
                assertThat(entry.constraint()).isEqualTo(decision.resource());
            });
            assertThatPlan(plan).satisfiesAllInvariants(decision, SUPPORTED_SIGNALS);
        }

        @Test
        @DisplayName("implicit Mapper substitutes the RAP output with the resource value")
        @SuppressWarnings("unchecked")
        void givenImplicitMapperWhenAppliedThenReturnsResource() {
            val decision       = permit("[]", "[]", "\"the-resource\"");
            val plan           = plannerWith().plan(decision, SUPPORTED_SIGNALS);
            val resourceMapper = (ConstraintHandler.Mapper<Object>) plan.entriesFor(OUTPUT_STRING_TYPE).getFirst()
                    .handler();
            assertThat(resourceMapper.apply("ignored-rap-output")).isEqualTo("the-resource");
        }

        @Test
        @DisplayName("resource present but no OutputSignal in supportedSignals: INADMISSIBLE substitute at decision")
        void givenResourceWithNoOutputSignalThenInadmissibleSubstitute() {
            val signalsWithoutOutput = Set.of(DECISION_SIGNAL_TYPE, INPUT_SIGNAL_TYPE, CANCEL_SIGNAL_TYPE);
            val decision             = permit("[]", "[]", "\"orphan\"");

            val plan = plannerWith().plan(decision, signalsWithoutOutput);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE)).isEmpty();
            assertSubstituteFailsWithReason(plan, DECISION_SIGNAL_TYPE, SubstitutionReason.INADMISSIBLE);
            assertThatPlan(plan).satisfiesAllInvariants(decision, signalsWithoutOutput);
        }

        @Test
        @DisplayName("malformed resource fails the obligation at runtime via AccessConstraintViolationException")
        @SuppressWarnings("unchecked")
        void givenMalformedResourceWhenMapperAppliedThenThrows() {
            val decision       = permit("[]", "[]", """
                    {"not": "a-string"}
                    """);
            val plan           = plannerWith().plan(decision, SUPPORTED_SIGNALS);
            val resourceMapper = (ConstraintHandler.Mapper<Object>) plan.entriesFor(OUTPUT_STRING_TYPE).getFirst()
                    .handler();
            assertThatThrownBy(() -> resourceMapper.apply("anything")).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot map resource");
        }

        @Test
        @DisplayName("user mapper at MIN_VALUE on the same signal collides with implicit and both are replaced")
        void givenUserMapperAtMinValueThenNonCommutingGroup() {
            val decision = permit("""
                    [{"id": "user"}]
                    """, "[]", "\"resource\"");
            when(provider.getConstraintHandler(id("user"), SUPPORTED_SIGNALS))
                    .thenReturn(Optional.of(scoped(mapper(), OUTPUT_STRING_TYPE, Integer.MIN_VALUE)));

            val plan = plannerWith(provider).plan(decision, SUPPORTED_SIGNALS);

            assertThat(plan.entriesFor(OUTPUT_STRING_TYPE)).hasSize(2)
                    .allSatisfy(entry -> assertThat(entry.handler()).isInstanceOf(ConstraintHandler.Runner.class));
            assertSubstituteFailsWithReason(plan, OUTPUT_STRING_TYPE, SubstitutionReason.NON_COMMUTING_GROUP);
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
        assertThatThrownBy(runner::run).isInstanceOf(AccessDeniedException.class).hasMessageContaining(reason.name());
    }
}
