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
package io.sapl.compiler.document;

import io.sapl.compiler.expressions.SaplCompilerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Signed number parsing in array subscripts")
class SignedNumberParsingTests {

    @ValueSource(strings = { "[1,2,3][99999999999999999999]", "[1,2,3][-99999999999999999999]" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("an array index that overflows int range is reported as a compile error, not an uncaught failure")
    void whenArrayIndexOverflowsIntRangeThenCompilerExceptionIsRaised(String expression) {
        final ThrowingCallable compile = () -> compileExpression(expression);

        assertThatThrownBy(compile).isInstanceOf(SaplCompilerException.class);
    }

    @ValueSource(strings = { "1E1000000000", "1E-1000000000" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("a numeric literal whose decimal scale exceeds the bound is a compile error, not a live value that OOMs when serialised")
    void whenNumberLiteralScaleExceedsBoundThenCompilerExceptionIsRaised(String expression) {
        final ThrowingCallable compile = () -> compileExpression(expression);

        assertThatThrownBy(compile).isInstanceOf(SaplCompilerException.class);
    }
}
