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
package io.sapl.test.mocking.function;

import io.sapl.api.model.Value;
import org.junit.jupiter.api.Test;

import static io.sapl.test.Imports.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class FunctionMockAlwaysSameValueTests {

    private final Value alwaysReturnValue = Value.of("bar");

    @Test
    void test() {
        final var mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
        assertThat(mock.evaluateFunctionCall(Value.of(1))).isEqualTo(alwaysReturnValue);
    }

    @Test
    void test_multipleTimes() {
        final var mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(3));
        assertThat(mock.evaluateFunctionCall(Value.of(1))).isEqualTo(alwaysReturnValue);
        assertThat(mock.evaluateFunctionCall(Value.of(2))).isEqualTo(alwaysReturnValue);
        assertThat(mock.evaluateFunctionCall(Value.of(3))).isEqualTo(alwaysReturnValue);

        assertThatNoException().isThrownBy(mock::assertVerifications);
    }

    @Test
    void test_errorMessage() {
        final var mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
        assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
    }

}
