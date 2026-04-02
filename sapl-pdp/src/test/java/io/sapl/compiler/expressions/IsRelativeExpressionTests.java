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

import java.util.stream.Stream;

import io.sapl.api.model.PureOperator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for {@link PureOperator#isRelativeExpression()} propagation. Uses
 * subscription-dependent identifiers (subject, action, resource) to prevent
 * constant folding, so the compiled result remains a PureOperator.
 */
@DisplayName("isRelativeExpression propagation")
class IsRelativeExpressionTests {

    @Nested
    @DisplayName("non-relative expressions return false")
    class NonRelativeExpressions {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenNonRelativeThenFalse(String description, String expression) {
            val compiled = compileExpression(expression);
            assertThat(compiled).isInstanceOf(PureOperator.class)
                    .satisfies(c -> assertThat(((PureOperator) c).isRelativeExpression()).isFalse());
        }

        static Stream<Arguments> whenNonRelativeThenFalse() {
            return Stream.of(arguments("identifier", "subject"), arguments("comparison", "subject.name == \"alice\""),
                    arguments("negation", "!(subject == \"x\")"), arguments("arithmetic", "subject + 1"),
                    arguments("conjunction", "subject && action"), arguments("disjunction", "subject || action"),
                    arguments("regex", "subject =~ \"^[a-z]+$\""),
                    arguments("function call", "standard.length(subject)"), arguments("key step", "subject.name"),
                    arguments("index step", "subject[0]"), arguments("wildcard step", "subject.*"),
                    arguments("array with pure", "[subject, 1]"), arguments("object with pure", "{\"a\": subject}"));
        }
    }

    @Nested
    @DisplayName("relative expressions propagate through operators")
    class RelativePropagation {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenRelativeThenTrue(String description, String expression) {
            val compiled = compileExpression(expression);
            assertThat(compiled).isInstanceOf(PureOperator.class)
                    .satisfies(c -> assertThat(((PureOperator) c).isRelativeExpression()).isTrue());
        }

        static Stream<Arguments> whenRelativeThenTrue() {
            return Stream.of(arguments("relative value @", "@"), arguments("binary: @ on left", "@ + subject"),
                    arguments("binary: @ on right", "subject + @"), arguments("unary on @", "!@"),
                    arguments("array containing @", "[subject, @]"), arguments("object containing @", "{\"a\": @}"),
                    arguments("key step on @", "@.name"), arguments("index step on @", "@[0]"),
                    arguments("wildcard on @", "@.*"), arguments("comparison with @", "@ == subject"),
                    arguments("conjunction with @", "@ && subject"), arguments("disjunction with @", "@ || subject"),
                    arguments("function with @ arg", "standard.length(@)"),
                    arguments("nested: @ in arithmetic in comparison", "@ + subject > 0"));
        }
    }

    @Nested
    @DisplayName("filters and conditions consume relative context")
    class RelativeConsumption {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenFilterOrConditionThenNotRelative(String description, String expression) {
            val compiled = compileExpression(expression);
            // Condition steps with all-constant base fold to Value.
            // With subscription-dependent base, they remain PureOperator.
            if (compiled instanceof PureOperator po) {
                assertThat(po.isRelativeExpression()).isFalse();
            }
            // If folded to Value, isRelativeExpression is not applicable (correct).
        }

        static Stream<Arguments> whenFilterOrConditionThenNotRelative() {
            return Stream.of(arguments("condition step with pure base", "subject[?(@ > 1)]"),
                    arguments("condition step with pure condition", "subject[?(@ == action)]"));
        }
    }

}
