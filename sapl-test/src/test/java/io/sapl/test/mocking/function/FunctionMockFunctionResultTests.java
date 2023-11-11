/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.test.Imports.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class FunctionMockFunctionResultTests {

    private final Function<Val[], Val> returns = (call) -> {
        var param0 = call[0].get().asDouble();
        var param1 = call[1].get().asDouble();
        return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
    };

    @Test
    void test() {
        var mock = new FunctionMockFunctionResult("foo", returns, times(1));
        assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2))).isEqualTo(Val.of(true));
    }

    @Test
    void test_multipleTimes() {
        var mock = new FunctionMockFunctionResult("foo", returns, times(3));
        assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2))).isEqualTo(Val.of(true));
        assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(3))).isEqualTo(Val.of(false));
        assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(4))).isEqualTo(Val.of(true));

        assertThatNoException().isThrownBy(mock::assertVerifications);
    }

    @Test
    void test_errorMessage() {
        var mock = new FunctionMockFunctionResult("foo", returns, times(1));
        assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
    }

    @Test
    void test_invalidNumberParams_TooLess_Exception() {
        var val4 = Val.of(4);
        var mock = new FunctionMockFunctionResult("foo", returns, times(1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> mock.evaluateFunctionCall(val4));
    }

    @Test
    void test_invalidNumberParams_TooMuch_Ignored() {
        var mock = new FunctionMockFunctionResult("foo", returns, times(1));
        assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2), Val.of("ignored"))).isEqualTo(Val.of(true));
    }

}
