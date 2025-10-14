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
package io.sapl.grammar.sapl.impl;

import org.junit.jupiter.api.Test;

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;

class EvaluateLiteralsValuesTests {

    @Test
    void evaluateNullLiteral() {
        assertExpressionEvaluatesTo("null", "null");
    }

    @Test
    void evaluateTrueLiteral() {
        assertExpressionEvaluatesTo("true", "true");
    }

    @Test
    void evaluateFalseLiteral() {
        assertExpressionEvaluatesTo("false", "false");
    }

    @Test
    void evaluateStringLiteral() {
        assertExpressionEvaluatesTo("\"Otto\"", "\"Otto\"");
    }

    @Test
    void evaluateNumberLiteral() {
        assertExpressionEvaluatesTo("666", "666");
    }

    @Test
    void evaluateNumberLiteral2() {
        assertExpressionEvaluatesTo("1", "1.0");
    }

    @Test
    void evaluateEmptyObject() {
        assertExpressionEvaluatesTo("{}", "{}");
    }

    @Test
    void evaluateObject() {
        final var json = "{ \"key1\" : null, \"key2\" : true }";
        assertExpressionEvaluatesTo(json, json);
    }

    @Test
    void evaluateEmptyArray() {
        assertExpressionEvaluatesTo("[]", "[]");
    }

    @Test
    void evaluateArray() {
        final var json = "[null,true,false]";
        assertExpressionEvaluatesTo(json, json);
    }

}
