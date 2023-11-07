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
package io.sapl.spring.method.metadata;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

class SaplAttributeTests {

    @Test
    void whenToStringCalled_thenStringContainsTheKeywords() {
        var sut         = new SaplAttribute(null, null, null, null, null, null);
        var stringValue = sut.toString();
        assertAll(() -> assertThat(stringValue, containsString("subject")),
                () -> assertThat(stringValue, containsString("action")),
                () -> assertThat(stringValue, containsString("resource")),
                () -> assertThat(stringValue, containsString("genericsType")),
                () -> assertThat(stringValue, containsString("environment")));
    }

    @Test
    void whenPassingNonNull_thenStringContainsTheKeywords() {
        var sut         = new SaplAttribute(PreEnforce.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"),
                Integer.class);
        var stringValue = sut.toString();
        assertAll(() -> assertThat(stringValue, containsString("subject")),
                () -> assertThat(stringValue, containsString("action")),
                () -> assertThat(stringValue, containsString("resource")),
                () -> assertThat(stringValue, containsString("genericsType")),
                () -> assertThat(stringValue, containsString("environment")));
    }

    @Test
    void whenPassingNull_thenExpressionsAreNull() {
        var sut = new SaplAttribute(null, null, null, null, null, null);
        assertAll(() -> assertThat(sut.subjectExpression(), is(nullValue())),
                () -> assertThat(sut.actionExpression(), is(nullValue())),
                () -> assertThat(sut.resourceExpression(), is(nullValue())),
                () -> assertThat(sut.genericsType(), is(nullValue())),
                () -> assertThat(sut.environmentExpression(), is(nullValue())));
    }

    @Test
    void whenExpressions_thenExpressionsAreSet() {
        var sut = new SaplAttribute(PostEnforce.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), String.class);
        assertAll(() -> assertThat(sut.subjectExpression(), is(notNullValue())),
                () -> assertThat(sut.actionExpression(), is(notNullValue())),
                () -> assertThat(sut.resourceExpression(), is(notNullValue())),
                () -> assertThat(sut.environmentExpression(), is(notNullValue())),
                () -> assertThat(sut.annotationType(), is(PostEnforce.class)),
                () -> assertThat(sut.genericsType(), is(String.class)));
    }

    @Test
    void whenExpressionsSet_thenToStringContainsThem() {
        var sut         = new SaplAttribute(EnforceTillDenied.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), Map.class);
        var stringValue = sut.toString();
        assertAll(() -> assertThat(stringValue, containsString("19 + 1")),
                () -> assertThat(stringValue, containsString("1 ne 1")),
                () -> assertThat(stringValue, containsString("2 > 1 ? 'a' : 'b'")),
                () -> assertThat(stringValue, containsString("workersHolder.salaryByWorkers['John']")),
                () -> assertThat(sut.annotationType(), is(EnforceTillDenied.class)),
                () -> assertThat(sut.genericsType(), is(Map.class)));
    }

    private static Expression toExpression(String expression) {
        return new SpelExpressionParser().parseExpression(expression);
    }

}
