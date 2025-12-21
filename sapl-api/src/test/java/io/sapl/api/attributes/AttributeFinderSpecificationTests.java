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
package io.sapl.api.attributes;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AttributeFinderSpecification matching and collision detection
 * logic.
 */
class AttributeFinderSpecificationTests {

    private static final AttributeFinder DUMMY_FINDER = inv -> null;

    private static AttributeFinderInvocation createInvocation(String name, boolean isEnvAttr, int numArgs) {
        return new AttributeFinderInvocation("test-security", name, isEnvAttr ? null : Value.of("entity"),
                List.<Value>of(Value.of("arg")).subList(0, Math.min(1, numArgs)), // 0 or 1 args
                Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(1), 0, false);
    }

    @Test
    void when_hasVariableNumberOfArguments_withVarArgs_then_returnsTrue() {
        var spec = new AttributeFinderSpecification("test", "attr", false, List.of(), TextValue.class, DUMMY_FINDER);

        assertThat(spec.hasVariableNumberOfArguments()).isTrue();
    }

    @Test
    void when_hasVariableNumberOfArguments_withoutVarArgs_then_returnsFalse() {
        var spec = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);

        assertThat(spec.hasVariableNumberOfArguments()).isFalse();
    }

    @Test
    void when_fullyQualifiedName_called_then_concatenatesNamespaceAndAttribute() {
        var spec = new AttributeFinderSpecification("time", "now", true, List.of(), null, DUMMY_FINDER);

        assertThat(spec.fullyQualifiedName()).isEqualTo("time.now");
    }

    // === Collision Detection Tests ===

    @Test
    void when_collidesWith_differentNames_then_noCollision() {
        var spec1 = new AttributeFinderSpecification("time", "now", true, List.of(), null, DUMMY_FINDER);
        var spec2 = new AttributeFinderSpecification("time", "dayOfWeek", true, List.of(), null, DUMMY_FINDER);

        assertThat(spec1.collidesWith(spec2)).isFalse();
    }

    @Test
    void when_collidesWith_differentEntityTypes_then_noCollision() {
        var spec1 = new AttributeFinderSpecification("test", "attr", true, List.of(), null, DUMMY_FINDER); // env
                                                                                                           // attribute
        var spec2 = new AttributeFinderSpecification("test", "attr", false, List.of(), null, DUMMY_FINDER); // entity
                                                                                                            // attribute

        assertThat(spec1.collidesWith(spec2)).isFalse();
    }

    @Test
    void when_collidesWith_bothHaveVarArgs_then_collision() {
        var spec1 = new AttributeFinderSpecification("test", "attr", false, List.of(), TextValue.class, DUMMY_FINDER);
        var spec2 = new AttributeFinderSpecification("test", "attr", false, List.of(), NumberValue.class, DUMMY_FINDER);

        assertThat(spec1.collidesWith(spec2)).isTrue();
    }

    @Test
    void when_collidesWith_sameParameterCount_then_collision() {
        var spec1 = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);
        var spec2 = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(NumberValue.class), null, DUMMY_FINDER);

        assertThat(spec1.collidesWith(spec2)).isTrue();
    }

    @Test
    void when_collidesWith_differentParameterCounts_then_noCollision() {
        var spec1 = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);
        var spec2 = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class, NumberValue.class), null, DUMMY_FINDER);

        assertThat(spec1.collidesWith(spec2)).isFalse();
    }

    // === Matching Tests ===

    @Test
    void when_matches_differentName_then_noMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", false, List.of(), null, DUMMY_FINDER);
        var invocation = createInvocation("other.attr", false, 0);

        assertThat(spec.matches(invocation)).isEqualTo(Match.NO_MATCH);
    }

    @Test
    void when_matches_differentEntityType_then_noMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", true, List.of(), null, DUMMY_FINDER); // env
                                                                                                                // attribute
        var invocation = createInvocation("test.attr", false, 0); // entity attribute

        assertThat(spec.matches(invocation)).isEqualTo(Match.NO_MATCH);
    }

    @Test
    void when_matches_exactParameterCount_then_exactMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);
        var invocation = createInvocation("test.attr", false, 1);

        assertThat(spec.matches(invocation)).isEqualTo(Match.EXACT_MATCH);
    }

    @Test
    void when_matches_varArgsWithEnoughArguments_then_varArgsMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), TextValue.class, DUMMY_FINDER);
        var invocation = new AttributeFinderInvocation("test-security", "test.attr", Value.of("entity"),
                List.<Value>of(Value.of("arg1"), Value.of("arg2")), Map.of(), Duration.ofSeconds(1),
                Duration.ofSeconds(30), Duration.ofSeconds(1), 0, false);

        assertThat(spec.matches(invocation)).isEqualTo(Match.VARARGS_MATCH);
    }

    @Test
    void when_matches_varArgsWithTooFewArguments_then_noMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class, NumberValue.class), // requires 2 fixed params
                TextValue.class, DUMMY_FINDER);
        var invocation = createInvocation("test.attr", false, 1); // only 1 arg

        assertThat(spec.matches(invocation)).isEqualTo(Match.NO_MATCH);
    }

    @Test
    void when_matches_wrongParameterCount_then_noMatch() {
        var spec       = new AttributeFinderSpecification("test", "attr", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);
        var invocation = createInvocation("test.attr", false, 0); // no args

        assertThat(spec.matches(invocation)).isEqualTo(Match.NO_MATCH);
    }
}
