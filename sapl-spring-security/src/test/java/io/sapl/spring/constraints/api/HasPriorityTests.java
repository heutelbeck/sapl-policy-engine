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
package io.sapl.spring.constraints.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class HasPriorityTests {

    @Test
    void minimalPriorityIsZero() {
        final var sut = spy(HasPriority.class);
        assertEquals(0, sut.getPriority());
    }

    @Test
    void compares_priorityAscending() {
        final var sutA = spy(HasPriority.class);
        final var sutB = spy(HasPriority.class);
        when(sutA.getPriority()).thenReturn(-100);
        assertThat(sutB).isLessThan(sutA);
        assertThat(sutA).isGreaterThan(sutB).isEqualByComparingTo(sutA);
    }
}
