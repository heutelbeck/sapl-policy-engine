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

import org.assertj.core.api.AbstractAssert;

import io.sapl.spring.util.Maybe;

/**
 * AssertJ custom assertion for {@link EnforcementResult}, exposing chainable
 * methods for value presence,
 * present payload, and failure state.
 */
final class EnforcementResultAssert extends AbstractAssert<EnforcementResultAssert, EnforcementResult<?>> {

    private EnforcementResultAssert(EnforcementResult<?> actual) {
        super(actual, EnforcementResultAssert.class);
    }

    static EnforcementResultAssert assertThatResult(EnforcementResult<?> actual) {
        return new EnforcementResultAssert(actual);
    }

    EnforcementResultAssert hasPresentValue(Object expected) {
        isNotNull();
        assertThat(actual.value()).as("Expected Present(%s) but was %s", expected, actual.value())
                .isInstanceOf(Maybe.Present.class);
        var present = (Maybe.Present<?>) actual.value();
        assertThat(present.value()).as("Present value mismatch").isEqualTo(expected);
        return this;
    }

    EnforcementResultAssert hasAbsentValue() {
        isNotNull();
        assertThat(actual.value()).as("Expected Absent but was %s", actual.value()).isInstanceOf(Maybe.Absent.class);
        return this;
    }

    EnforcementResultAssert hasFailureState(boolean expected) {
        isNotNull();
        assertThat(actual.failureState()).as("Expected failureState=%s", expected).isEqualTo(expected);
        return this;
    }
}
