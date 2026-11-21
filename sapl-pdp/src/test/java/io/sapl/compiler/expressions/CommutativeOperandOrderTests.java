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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureOperator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The commutative operators {@code ==}, {@code !=}, {@code *}, {@code &&},
 * {@code ||}, {@code &}, {@code |} and {@code ^} denote the same computation
 * whatever the order of their operands, so the policy index takes
 * {@code a == b} and {@code b == a} as one predicate, and the result, errors
 * included, does not depend on the order written.
 */
@DisplayName("Operand order of commutative operators")
class CommutativeOperandOrderTests {

    private static PureOperator compilePure(String expression) {
        return (PureOperator) compileExpression(expression);
    }

    @ParameterizedTest(name = "{0} and {1} are the same computation")
    @CsvSource(delimiter = ';', textBlock = """
            subject.a == action.b ; action.b == subject.a
            subject.a != action.b ; action.b != subject.a
            subject.a * action.b ; action.b * subject.a
            subject.a && action.b ; action.b && subject.a
            subject.a || action.b ; action.b || subject.a
            subject.a & action.b ; action.b & subject.a
            subject.a | action.b ; action.b | subject.a
            subject.a ^ action.b ; action.b ^ subject.a
            subject.a == 1 ; 1 == subject.a
            subject.a != "x" ; "x" != subject.a
            subject.a * 2 ; 2 * subject.a
            subject.a && true ; true && subject.a
            subject.a ^ false ; false ^ subject.a
            subject.a * action.b * resource.c ; resource.c * subject.a * action.b
            subject.a && action.b && resource.c ; resource.c && action.b && subject.a
            subject.a || action.b || resource.c ; action.b || resource.c || subject.a
            subject.a & action.b & resource.c ; resource.c & subject.a & action.b
            subject.a | action.b | resource.c ; action.b | subject.a | resource.c
            subject.a ^ action.b ^ resource.c ; resource.c ^ action.b ^ subject.a
            subject.a * 2 * action.b * 3 ; 3 * action.b * 2 * subject.a
            """)
    void whenOperandsAreSwappedThenSameComputationWithSameHash(String expression, String swapped) {
        val first  = compilePure(expression);
        val second = compilePure(swapped);
        assertThat(first.semanticHash()).isEqualTo(second.semanticHash());
        assertThat(first.semanticEquals(second)).isTrue();
    }

    @Test
    @DisplayName("the operands of + keep their order, as + concatenates texts")
    void whenOperandsOfAdditionAreSwappedThenDifferentComputation() {
        assertThat(compilePure("subject.a + action.b").semanticEquals(compilePure("action.b + subject.a"))).isFalse();
    }

    @ParameterizedTest(name = "{0} and {1} are the same error")
    @CsvSource(delimiter = ';', textBlock = """
            (1/0) == ("a" * 2) ; ("a" * 2) == (1/0)
            (1/0) != ("a" * 2) ; ("a" * 2) != (1/0)
            (1/0) * ("a" * 2) ; ("a" * 2) * (1/0)
            (1/0) * ("a" * 2) * subject ; subject * ("a" * 2) * (1/0)
            ("a" * 2) ^ (1/0) ^ subject ; subject ^ (1/0) ^ ("a" * 2)
            (subject * 2) == (action * 2) ; (action * 2) == (subject * 2)
            (subject * 2) * (action * 2) ; (action * 2) * (subject * 2)
            (subject > 1) && (action > 1) ; (action > 1) && (subject > 1)
            (subject > 1) || (action > 1) ; (action > 1) || (subject > 1)
            (subject > 1) & (action > 1) ; (action > 1) & (subject > 1)
            (subject > 1) | (action > 1) ; (action > 1) | (subject > 1)
            (subject > 1) ^ (action > 1) ; (action > 1) ^ (subject > 1)
            (subject > 1) && (action > 1) && (resource > 1) ; (resource > 1) && (subject > 1) && (action > 1)
            (subject > 1) ^ (action > 1) ^ (resource > 1) ; (action > 1) ^ (resource > 1) ^ (subject > 1)
            ("a" > 1) && (subject > 1) ; (subject > 1) && ("a" > 1)
            """)
    void whenSeveralOperandsAreErrorsThenTheErrorDoesNotDependOnTheOrderWritten(String expression, String swapped) {
        val result        = evaluateExpression(expression);
        val swappedResult = evaluateExpression(swapped);
        assertThat(swappedResult).isInstanceOf(ErrorValue.class);
        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).isEqualTo(((ErrorValue) swappedResult).message()));
    }
}
