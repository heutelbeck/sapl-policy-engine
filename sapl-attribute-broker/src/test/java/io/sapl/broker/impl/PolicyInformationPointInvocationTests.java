/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class PolicyInformationPointInvocationTests {

    private static final Duration BACKOFF    = Duration.ofMillis(50L);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1L);

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Val>        emptyList = List.of();
        final Map<String, Val> emptyMap  = Map.of();
        assertThatThrownBy(() -> new PolicyInformationPointInvocation(null, "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", null, Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "abc.def", Val.TRUE, null, emptyMap,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "abc.def", Val.TRUE, emptyList, null,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap,
                null, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, null, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, null, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("id", "123 ", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new PolicyInformationPointInvocation("id", "abc.def", null, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, BACKOFF, 20L));
    }

}
