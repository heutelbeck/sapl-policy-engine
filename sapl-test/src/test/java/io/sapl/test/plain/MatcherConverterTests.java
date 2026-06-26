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
package io.sapl.test.plain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.NumberValue;
import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser;
import io.sapl.test.grammar.antlr.SAPLTestParser.NodeMatcherContext;

@DisplayName("MatcherConverter")
class MatcherConverterTests {

    private static NodeMatcherContext parseNodeMatcher(String matcher) {
        final var lexer       = new SAPLTestLexer(CharStreams.fromString(matcher));
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser      = new SAPLTestParser(tokenStream);
        return parser.nodeMatcher();
    }

    @Nested
    @DisplayName("number matcher precision")
    class NumberMatcherPrecision {

        @Test
        @DisplayName("when literal exceeds double precision then it rejects a distinct neighbouring value")
        void whenLiteralExceedsDoublePrecisionThenItRejectsDistinctNeighbour() {
            final var converted   = MatcherConverter.convertNodeMatcher(parseNodeMatcher("number 9007199254740993"));
            final var sameLiteral = new NumberValue(new BigDecimal("9007199254740993"));
            final var neighbour   = new NumberValue(new BigDecimal("9007199254740992"));
            assertThat(converted.matches(sameLiteral)).isTrue();
            assertThat(converted.matches(neighbour)).isFalse();
        }

        @Test
        @DisplayName("when literal is a non-representable decimal then it matches the exact BigDecimal value")
        void whenLiteralIsNonRepresentableDecimalThenItMatchesExactValue() {
            final var converted = MatcherConverter.convertNodeMatcher(parseNodeMatcher("number 0.1"));
            assertThat(converted.matches(new NumberValue(new BigDecimal("0.1")))).isTrue();
        }
    }
}
