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
package io.sapl.attributes.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;

class AttributeFinderInvocationTests {

    private static final Duration FIFTY_MILLISECONDS = Duration.ofMillis(50L);
    private static final Duration ONE_SECOND         = Duration.ofSeconds(1L);

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Val>        emptyList = List.of();
        final Map<String, Val> emptyMap  = Map.of();
        assertThatThrownBy(() -> new AttributeFinderInvocation(null, "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", null, Val.TRUE, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "abc.def", Val.TRUE, null, emptyMap, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "abc.def", Val.TRUE, emptyList, null, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap, null,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, null, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, null, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("id", "123 ", Val.TRUE, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new AttributeFinderInvocation("id", "abc.def", null, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, FIFTY_MILLISECONDS, 20L, false));
    }

}
