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
package io.sapl.spring.method.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SaplAttributeTests {

    @Test
    void whenToStringCalled_thenStringContainsTheKeywords() {
        var sut         = new SaplAttribute(null, null, null, null, null, null, null);
        var stringValue = sut.toString();
        assertThat(stringValue).contains("subject", "action", "resource", "environment", "secrets", "genericsType");
    }

    @Test
    void whenPassingNonNull_thenStringContainsTheKeywords() {
        var sut         = new SaplAttribute(PreEnforce.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), null,
                Integer.class);
        var stringValue = sut.toString();
        assertThat(stringValue).contains("subject", "action", "resource", "environment", "secrets", "genericsType");
    }

    @Test
    void whenPassingNull_thenExpressionsAreNull() {
        var sut = new SaplAttribute(null, null, null, null, null, null, null);
        assertThat(sut).satisfies(s -> {
            assertThat(s.subjectExpression()).isNull();
            assertThat(s.actionExpression()).isNull();
            assertThat(s.resourceExpression()).isNull();
            assertThat(s.environmentExpression()).isNull();
            assertThat(s.secretsExpression()).isNull();
            assertThat(s.genericsType()).isNull();
        });
    }

    @Test
    void whenExpressions_thenExpressionsAreSet() {
        var sut = new SaplAttribute(PostEnforce.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"),
                toExpression("{key: 'value'}"), String.class);
        assertThat(sut).satisfies(s -> {
            assertThat(s.subjectExpression()).isNotNull();
            assertThat(s.actionExpression()).isNotNull();
            assertThat(s.resourceExpression()).isNotNull();
            assertThat(s.environmentExpression()).isNotNull();
            assertThat(s.secretsExpression()).isNotNull();
            assertThat(s.annotationType()).isEqualTo(PostEnforce.class);
            assertThat(s.genericsType()).isEqualTo(String.class);
        });
    }

    @Test
    void whenExpressionsSet_thenToStringContainsThem() {
        var sut         = new SaplAttribute(EnforceTillDenied.class, toExpression("19 + 1"), toExpression("1 ne 1"),
                toExpression("2 > 1 ? 'a' : 'b'"), toExpression("workersHolder.salaryByWorkers['John']"), null,
                Map.class);
        var stringValue = sut.toString();
        assertThat(stringValue).contains("19 + 1", "1 ne 1", "2 > 1 ? 'a' : 'b'",
                "workersHolder.salaryByWorkers['John']");
        assertThat(sut.annotationType()).isEqualTo(EnforceTillDenied.class);
        assertThat(sut.genericsType()).isEqualTo(Map.class);
    }

    @Test
    void whenSecretsNull_thenToStringShowsNoSecrets() {
        var sut         = new SaplAttribute(null, null, null, null, null, null, null);
        var stringValue = sut.toString();
        assertThat(stringValue).contains("secrets=NO SECRETS");
    }

    @Test
    void whenSecretsSet_thenToStringShowsRedacted() {
        var sut         = new SaplAttribute(null, null, null, null, null, toExpression("{key: 'secretValue'}"), null);
        var stringValue = sut.toString();
        assertThat(stringValue).contains("secrets=SECRETS REDACTED").doesNotContain("secretValue");
    }

    private static Expression toExpression(String expression) {
        return new SpelExpressionParser().parseExpression(expression);
    }

}
