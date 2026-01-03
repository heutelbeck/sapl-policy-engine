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
package io.sapl.util;

import io.sapl.api.model.Value;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTestUtilTests {

    @Test
    void parseExpression_withLiteral_returnsLiteralAst() {
        val expr = parseExpression("42");
        assertThat(expr).isInstanceOf(Literal.class);
        assertThat(((Literal) expr).value()).isEqualTo(Value.of(42));
    }

    @Test
    void parseExpression_withString_returnsLiteralAst() {
        val expr = parseExpression("\"hello\"");
        assertThat(expr).isInstanceOf(Literal.class);
        assertThat(((Literal) expr).value()).isEqualTo(Value.of("hello"));
    }

    @Test
    void evaluateExpression_withLiteral_returnsValue() {
        val result = evaluateExpression("42");
        assertThat(result).isEqualTo(Value.of(42));
    }

    @Test
    void evaluateExpression_withBoolean_returnsValue() {
        val result = evaluateExpression("true");
        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void evaluateExpression_withNull_returnsNull() {
        val result = evaluateExpression("null");
        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void evaluateExpression_withVariable_returnsVariableValue() {
        val ctx    = withVariables(Map.of("x", Value.of(100)));
        val result = evaluateExpression("x", ctx);
        assertThat(result).isEqualTo(Value.of(100));
    }

    @Test
    void evaluateExpression_withUndefinedVariable_returnsUndefined() {
        val result = evaluateExpression("unknownVar");
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void emptyContext_hasNoVariables() {
        val ctx = emptyContext();
        assertThat(ctx.get("anything")).isEqualTo(Value.UNDEFINED);
    }

}
