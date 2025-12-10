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
import io.sapl.test.SaplTestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FunctionMockSequenceTests {

    private final Value[] seq = new Value[] { Value.of(1), Value.of(2), Value.of(3) };

    @Test
    void test() {
        final var mock = new FunctionMockSequence("foo");
        mock.loadMockReturnValue(seq);
        assertThat(mock.evaluateFunctionCall(Value.of("do"))).isEqualTo(seq[0]);
        assertThat(mock.evaluateFunctionCall(Value.of("not"))).isEqualTo(seq[1]);
        assertThat(mock.evaluateFunctionCall(Value.of("matter"))).isEqualTo(seq[2]);
    }

    @Test
    void test_tooManyCalls() {
        final var aVal = Value.of("returnValueUndefined");
        final var mock = new FunctionMockSequence("foo");
        mock.loadMockReturnValue(seq);
        assertThat(mock.evaluateFunctionCall(Value.of("do"))).isEqualTo(seq[0]);
        assertThat(mock.evaluateFunctionCall(Value.of("not"))).isEqualTo(seq[1]);
        assertThat(mock.evaluateFunctionCall(Value.of("matter"))).isEqualTo(seq[2]);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluateFunctionCall(aVal));
    }

    @Test
    void test_tooFewCalls() {
        final var mock = new FunctionMockSequence("foo");
        mock.loadMockReturnValue(seq);
        assertThat(mock.evaluateFunctionCall(Value.of("do"))).isEqualTo(seq[0]);
        assertThat(mock.evaluateFunctionCall(Value.of("not"))).isEqualTo(seq[1]);

        assertThatExceptionOfType(AssertionError.class).isThrownBy(mock::assertVerifications);
    }

    @Test
    void test_errorMessage() {
        final var mock = new FunctionMockSequence("foo");
        assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
    }

}
