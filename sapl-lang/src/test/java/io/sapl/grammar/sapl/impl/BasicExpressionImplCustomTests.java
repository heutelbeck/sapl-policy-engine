/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;

import org.junit.jupiter.api.Test;

class BasicExpressionImplCustomTests {

    @Test
    void basicExpressionWithStep() {
        assertExpressionEvaluatesTo("[ null ].[0]", "null");
    }

    @Test
    void basicExpressionWithFilter() {
        assertExpressionEvaluatesTo("null |- mock.emptyString", "\"\"");
    }

    @Test
    void subTemplateNoArray() {
        assertExpressionEvaluatesTo("null :: { \"name\" : @ }", "{ \"name\" : null }");
    }

    @Test
    void subTemplateArray() {
        assertExpressionEvaluatesTo("[true, false] :: null", "[ null,null ]");
    }

    @Test
    void subTemplateEmptyArray() {
        assertExpressionEvaluatesTo("[] :: null", "[]");
    }

}
