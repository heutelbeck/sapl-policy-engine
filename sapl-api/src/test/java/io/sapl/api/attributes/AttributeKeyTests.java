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

import io.sapl.api.model.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AttributeKey equality and factory method.
 */
class AttributeKeyTests {

    @Test
    void when_of_createsKeyFromInvocation_then_extractsCorrectComponents() {
        var invocation = new AttributeFinderInvocation("test-security", "test.attr", Value.of("user123"),
                List.of(Value.of("arg1"), Value.of("arg2")), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(30),
                Duration.ofSeconds(1), 0, false);

        var key = AttributeKey.of(invocation);

        assertThat(key).satisfies(k -> {
            assertThat(k.entity()).isEqualTo(Value.of("user123"));
            assertThat(k.attributeName()).isEqualTo("test.attr");
            assertThat(k.arguments()).containsExactly(Value.of("arg1"), Value.of("arg2"));
        });
    }

    @Test
    void when_of_environmentAttribute_then_entityIsNull() {
        var invocation = new AttributeFinderInvocation("test-security", "time.now", List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(1), 0, false);

        var key = AttributeKey.of(invocation);

        assertThat(key).satisfies(k -> {
            assertThat(k.entity()).isNull();
            assertThat(k.attributeName()).isEqualTo("time.now");
        });
    }

    @Test
    void when_equals_sameComponents_then_areEqual() {
        var key1 = new AttributeKey(Value.of("entity"), "test.attr", List.of(Value.of("arg")));
        var key2 = new AttributeKey(Value.of("entity"), "test.attr", List.of(Value.of("arg")));

        assertThat(key1).isEqualTo(key2).hasSameHashCodeAs(key2);
    }

    @Test
    void when_equals_differentEntity_then_notEqual() {
        var key1 = new AttributeKey(Value.of("entity1"), "test.attr", List.of());
        var key2 = new AttributeKey(Value.of("entity2"), "test.attr", List.of());

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void when_equals_differentAttributeName_then_notEqual() {
        var key1 = new AttributeKey(Value.of("entity"), "test.attr1", List.of());
        var key2 = new AttributeKey(Value.of("entity"), "test.attr2", List.of());

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void when_equals_differentArguments_then_notEqual() {
        var key1 = new AttributeKey(Value.of("entity"), "test.attr", List.of(Value.of("arg1")));
        var key2 = new AttributeKey(Value.of("entity"), "test.attr", List.of(Value.of("arg2")));

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void when_equals_nullEntityVsNonNull_then_notEqual() {
        var key1 = new AttributeKey(null, "test.attr", List.of());
        var key2 = new AttributeKey(Value.of("entity"), "test.attr", List.of());

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void when_equals_bothNullEntity_then_areEqual() {
        var key1 = new AttributeKey(null, "test.attr", List.of());
        var key2 = new AttributeKey(null, "test.attr", List.of());

        assertThat(key1).isEqualTo(key2).hasSameHashCodeAs(key2);
    }
}
