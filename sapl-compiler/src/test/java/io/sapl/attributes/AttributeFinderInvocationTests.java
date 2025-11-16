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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AttributeFinderInvocationTests {

    private static final Duration FIFTY_MILLISECONDS = Duration.ofMillis(50L);
    private static final Duration ONE_SECOND         = Duration.ofSeconds(1L);

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Value> emptyList = List.of();
        assertThatThrownBy(() -> new AttributeFinderInvocation(null, Value.TRUE, emptyList, ONE_SECOND, ONE_SECOND,
                FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("abc.def", Value.TRUE, null, ONE_SECOND, ONE_SECOND,
                FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("abc.def", Value.TRUE, emptyList, null, ONE_SECOND,
                FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("abc.def", Value.TRUE, emptyList, ONE_SECOND, null,
                FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("abc.def", Value.TRUE, emptyList, ONE_SECOND, ONE_SECOND,
                null, 20L, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AttributeFinderInvocation("123 ", Value.TRUE, emptyList, ONE_SECOND, ONE_SECOND,
                FIFTY_MILLISECONDS, 20L, false)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new AttributeFinderInvocation("abc.def", null, emptyList, ONE_SECOND, ONE_SECOND,
                FIFTY_MILLISECONDS, 20L, false));
    }

}
