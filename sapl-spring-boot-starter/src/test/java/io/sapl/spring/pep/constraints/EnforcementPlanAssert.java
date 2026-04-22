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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.AbstractAssert;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;

/**
 * AssertJ custom assertion for {@link EnforcementPlan}, exposing one chainable
 * method per paper invariant
 * plus {@link #satisfiesAllInvariants(AuthorizationDecision, Set)} that runs
 * all of them. Each invariant
 * method names its paper number in failure messages.
 */
final class EnforcementPlanAssert extends AbstractAssert<EnforcementPlanAssert, EnforcementPlan> {

    private EnforcementPlanAssert(EnforcementPlan actual) {
        super(actual, EnforcementPlanAssert.class);
    }

    static EnforcementPlanAssert assertThatPlan(EnforcementPlan actual) {
        return new EnforcementPlanAssert(actual);
    }

    EnforcementPlanAssert satisfiesAllInvariants(AuthorizationDecision decision, Set<SignalType> supportedSignals) {
        return satisfiesOrdering().satisfiesTypeSignalAdmissibility().satisfiesMapperTag()
                .satisfiesMapperCommutativity().satisfiesSupportedSignals(supportedSignals).satisfiesCoverage(decision);
    }

    EnforcementPlanAssert satisfiesOrdering() {
        isNotNull();
        actual.entries().forEach((signal, entries) -> {
            for (var i = 1; i < entries.size(); i++) {
                var previous = entries.get(i - 1);
                var current  = entries.get(i);
                assertThat(previous.compareTo(current))
                        .as("Invariant 1 (Ordering) violated at signal %s, position %d", signal, i)
                        .isLessThanOrEqualTo(0);
            }
        });
        return this;
    }

    EnforcementPlanAssert satisfiesTypeSignalAdmissibility() {
        isNotNull();
        actual.entries().forEach((signal, entries) -> entries.forEach(entry -> {
            var dataCarrying = signal instanceof SignalType.ValueSignalType<?>;
            if (entry.handler() instanceof ConstraintHandler.Mapper<?>
                    || entry.handler() instanceof ConstraintHandler.Consumer<?>) {
                assertThat(dataCarrying)
                        .as("Invariant 2 (Type-signal admissibility) violated: %s at non-data-carrying signal %s",
                                entry.handler().getClass().getSimpleName(), signal)
                        .isTrue();
            }
        }));
        return this;
    }

    EnforcementPlanAssert satisfiesMapperTag() {
        isNotNull();
        actual.entries().values().stream().flatMap(List::stream)
                .filter(entry -> entry.handler() instanceof ConstraintHandler.Mapper<?>)
                .forEach(entry -> assertThat(entry.constraintType())
                        .as("Invariant 3 (Mapper tag) violated for entry %s", entry)
                        .isEqualTo(ConstraintType.OBLIGATION));
        return this;
    }

    EnforcementPlanAssert satisfiesMapperCommutativity() {
        isNotNull();
        actual.entries().forEach((signal, entries) -> {
            var index = 0;
            while (index < entries.size()) {
                if (!(entries.get(index).handler() instanceof ConstraintHandler.Mapper<?>)) {
                    index++;
                    continue;
                }
                var priority  = entries.get(index).priority();
                var groupSize = 0;
                while (index < entries.size() && entries.get(index).handler() instanceof ConstraintHandler.Mapper<?>
                        && entries.get(index).priority() == priority) {
                    groupSize++;
                    index++;
                }
                assertThat(groupSize)
                        .as("Invariant 4 (Mapper commutativity) violated at signal %s, priority %d", signal, priority)
                        .isLessThanOrEqualTo(1);
            }
        });
        return this;
    }

    EnforcementPlanAssert satisfiesSupportedSignals(Set<SignalType> supportedSignals) {
        isNotNull();
        actual.entries().keySet().forEach(signal -> assertThat(supportedSignals)
                .as("Invariant 5 (Supported signals) violated: plan contains entry at unsupported signal %s", signal)
                .contains(signal));
        return this;
    }

    EnforcementPlanAssert satisfiesCoverage(AuthorizationDecision decision) {
        isNotNull();
        var expectedConstraints = new HashSet<Value>();
        expectedConstraints.addAll(decision.obligations());
        expectedConstraints.addAll(decision.advice());
        var coveredConstraints = actual.entries().values().stream().flatMap(List::stream)
                .map(EnforcementPlanEntry::constraint).collect(java.util.stream.Collectors.toCollection(HashSet::new));
        var totalEntries       = actual.entries().values().stream().mapToInt(List::size).sum();
        assertThat(coveredConstraints).as("Invariant 6 (Coverage) violated: covered constraints differ from O union A")
                .isEqualTo(expectedConstraints);
        assertThat(totalEntries).as("Invariant 6 (Coverage) violated: expected one entry per constraint")
                .isEqualTo(decision.obligations().size() + decision.advice().size());
        return this;
    }
}
