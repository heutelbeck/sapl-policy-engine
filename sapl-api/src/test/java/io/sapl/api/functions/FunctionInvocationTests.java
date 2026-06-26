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
package io.sapl.api.functions;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionInvocationTests {

    private static FunctionSpecification varargsSpecWithOneFixedParameter() {
        return new FunctionSpecification("util", "concat", List.of(TextValue.class), TextValue.class,
                invocation -> null);
    }

    private static FunctionInvocation invocationWithArguments(int count) {
        final var arguments = Stream.<Value>generate(() -> Value.of("x")).limit(count).toList();
        return new FunctionInvocation("util.concat", arguments);
    }

    @Nested
    @DisplayName("varargs arity guard")
    class VarargsArity {

        @Test
        @DisplayName("invocation with fewer arguments than the fixed parameter count does not match a varargs function")
        void whenFewerArgumentsThanFixedParametersThenNoMatch() {
            final var spec   = varargsSpecWithOneFixedParameter();
            final var result = invocationWithArguments(0).matches(spec);
            assertThat(result).isEqualTo(Match.NO_MATCH);
        }

        @Test
        @DisplayName("invocation with exactly the fixed parameter count matches exactly")
        void whenExactlyFixedParameterCountThenExactMatch() {
            final var spec   = varargsSpecWithOneFixedParameter();
            final var result = invocationWithArguments(1).matches(spec);
            assertThat(result).isEqualTo(Match.EXACT_MATCH);
        }

        @Test
        @DisplayName("invocation with more arguments than the fixed parameter count is a varargs match")
        void whenMoreArgumentsThanFixedParameterCountThenVarargsMatch() {
            final var spec   = varargsSpecWithOneFixedParameter();
            final var result = invocationWithArguments(3).matches(spec);
            assertThat(result).isEqualTo(Match.VARARGS_MATCH);
        }
    }
}
