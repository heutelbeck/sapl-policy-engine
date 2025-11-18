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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeFinderInvocationTests {

    private static final Duration           FIFTY_MILLISECONDS = Duration.ofMillis(50L);
    private static final Duration           ONE_SECOND         = Duration.ofSeconds(1L);
    private static final String             CONFIG_ID          = "test-config";
    private static final List<Value>        EMPTY_ARGS         = List.of();
    private static final Map<String, Value> EMPTY_VARS         = Map.of();

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        // Null configurationId
        assertThatThrownBy(() -> new AttributeFinderInvocation(null, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null attributeName
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, null, Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null arguments
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, null, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null variables
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, null,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null initialTimeOut
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                null, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null pollInterval
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, null, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);

        // Null backoff
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, null, 20L, false)).isInstanceOf(NullPointerException.class);

        // Invalid attribute name (starts with digit followed by space)
        assertThatThrownBy(() -> new AttributeFinderInvocation(CONFIG_ID, "123 ", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(IllegalArgumentException.class);

        // Valid construction with null entity (environment attribute)
        assertDoesNotThrow(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", null, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false));

        // Valid construction with entity
        assertDoesNotThrow(() -> new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false));
    }

    @Test
    void whenConstructingWithEnvironmentAttributeConstructorThenEntityIsNull() {
        val invocation = new AttributeFinderInvocation(CONFIG_ID, "abc.def", EMPTY_ARGS, EMPTY_VARS, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 3L, false);

        assertTrue(invocation.isEnvironmentAttributeInvocation());
        assertEquals(null, invocation.entity());
    }

    @Test
    void whenConstructingWithEntityThenIsNotEnvironmentAttribute() {
        val invocation = new AttributeFinderInvocation(CONFIG_ID, "abc.def", Value.TRUE, EMPTY_ARGS, EMPTY_VARS,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 3L, false);

        assertFalse(invocation.isEnvironmentAttributeInvocation());
        assertEquals(Value.TRUE, invocation.entity());
    }

    @Test
    void whenConstructingInvocationThenAllFieldsAreCorrectlySet() {
        val                entity    = Value.of("test-entity");
        List<Value>        arguments = List.of(Value.of(1), Value.of("arg2"));
        Map<String, Value> variables = Map.of("var1", Value.of("value1"));
        val                retries   = 5L;
        val                fresh     = true;

        val invocation = new AttributeFinderInvocation(CONFIG_ID, "test.attribute", entity, arguments, variables,
                ONE_SECOND, FIFTY_MILLISECONDS, Duration.ofMillis(100L), retries, fresh);

        assertEquals(CONFIG_ID, invocation.configurationId());
        assertEquals("test.attribute", invocation.attributeName());
        assertEquals(entity, invocation.entity());
        assertEquals(arguments, invocation.arguments());
        assertEquals(variables, invocation.variables());
        assertEquals(ONE_SECOND, invocation.initialTimeOut());
        assertEquals(FIFTY_MILLISECONDS, invocation.pollInterval());
        assertEquals(Duration.ofMillis(100L), invocation.backoff());
        assertEquals(retries, invocation.retries());
        assertEquals(fresh, invocation.fresh());
    }

}
