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
package io.sapl.compiler.expressions;

import io.sapl.api.model.PureOperator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expressions that compute the same are the same computation wherever they are
 * written, which lets the index and the sharing of subexpressions merge them.
 * This holds for operators whose parts are held in arrays as well.
 */
@DisplayName("Semantic equality of compiled expressions")
class SemanticEqualityTests {

    private static PureOperator compilePure(String expression) {
        return (PureOperator) compileExpression(expression);
    }

    @ParameterizedTest(name = "{0} and {1} are the same computation")
    @CsvSource(delimiter = ';', textBlock = """
            {"a": subject, "b": action}       ; { "a" : subject , "b" : action }
            [subject, {"a": action}]          ; [ subject , { "a" : action } ]
            {"a": {"b": subject}}             ; {"a":{"b":subject}}
            """)
    @DisplayName("equal expressions are equal and hash alike")
    void whenExpressionsAreEqualThenSameComputationWithSameHash(String expression, String sameElsewhere) {
        val first  = compilePure(expression);
        val second = compilePure(sameElsewhere);

        assertThat(first.semanticEquals(second)).isTrue();
        assertThat(first.semanticHash()).isEqualTo(second.semanticHash());
    }

    @ParameterizedTest(name = "{0} and {1} are different computations")
    @CsvSource(delimiter = ';', textBlock = """
            {"a": subject} ; {"b": subject}
            {"a": subject} ; {"a": action}
            {"a": subject, "b": action} ; {"b": action, "a": subject}
            """)
    @DisplayName("expressions with other keys, values or key order are different")
    void whenExpressionsDifferThenDifferentComputations(String expression, String other) {
        assertThat(compilePure(expression).semanticEquals(compilePure(other))).isFalse();
    }
}
