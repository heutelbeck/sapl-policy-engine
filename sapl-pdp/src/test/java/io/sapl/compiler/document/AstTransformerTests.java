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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("AstTransformer")
class AstTransformerTests {

    @Nested
    @DisplayName("numeric literals at the grammar boundary")
    class NumericLiteralBoundaryTests {

        // A SAPL author can write a numeric literal whose exponent the lexer
        // accepts as a NUMBER token but which no BigDecimal can represent.
        // Compiling such a document must report a structured validation error,
        // never crash the compile/validation path with a raw NumberFormatException.
        @ParameterizedTest(name = "{0}")
        @MethodSource("grammarValidButUnrepresentableNumbers")
        @DisplayName("reports invalid document instead of crashing on unrepresentable literal")
        void whenLiteralExponentExceedsBigDecimalRangeThenDocumentIsInvalid(String description, String definition) {
            val parse = (Runnable) () -> DocumentCompiler.parseDocument(definition);

            assertThatCode(parse::run).doesNotThrowAnyException();
            val document = DocumentCompiler.parseDocument(definition);
            assertThat(document.isInvalid()).isTrue();
        }

        // A literal with millions of digits is lexically valid but would drive an
        // expensive BigDecimal construction. It must be rejected at the length cap.
        @Test
        @DisplayName("reports invalid document instead of building a giant BigDecimal for an over-length literal")
        void whenNumericLiteralExceedsLengthCapThenDocumentIsInvalid() {
            val definition = "policy \"p\" permit " + "9".repeat(1001) + " == 1;";
            val parse      = (Runnable) () -> DocumentCompiler.parseDocument(definition);

            assertThatCode(parse::run).doesNotThrowAnyException();
            assertThat(DocumentCompiler.parseDocument(definition).isInvalid()).isTrue();
        }

        static Stream<Arguments> grammarValidButUnrepresentableNumbers() {
            return Stream.of(arguments("oversized positive exponent", "policy \"p\" permit 1e9999999999 == 1;"),
                    arguments("oversized negative exponent", "policy \"p\" permit 1e-9999999999 == 1;"));
        }
    }

    @Nested
    @DisplayName("array subscript numbers at the grammar boundary")
    class ArraySubscriptBoundaryTests {

        // Array index, slice, and index-union subscripts accept any NUMBER token,
        // including values outside the int range or in scientific/decimal form.
        // Compiling such a document must surface a structured validation error,
        // never escape the compiler with a raw NumberFormatException.
        @ParameterizedTest(name = "{0}")
        @MethodSource("grammarValidButNonIntSubscripts")
        @DisplayName("reports invalid document instead of crashing on non-int subscript")
        void whenSubscriptIsNotRepresentableAsIntThenDocumentIsInvalid(String description, String definition) {
            val parse = (Runnable) () -> DocumentCompiler.parseDocument(definition);

            assertThatCode(parse::run).doesNotThrowAnyException();
            val document = DocumentCompiler.parseDocument(definition);
            assertThat(document.isInvalid()).isTrue();
        }

        static Stream<Arguments> grammarValidButNonIntSubscripts() {
            return Stream.of(arguments("out-of-range index", "policy \"p\" permit [1,2,3][9999999999] == 1;"),
                    arguments("scientific-notation index", "policy \"p\" permit [1,2,3][1e3] == 1;"),
                    arguments("out-of-range slice bound", "policy \"p\" permit [1,2,3][9999999999:] == 1;"),
                    arguments("out-of-range index-union member", "policy \"p\" permit [1,2,3][0,9999999999] == 1;"));
        }
    }

    @Nested
    @DisplayName("backtick-escaped identifiers and tight XOR")
    class EscapingTests {

        @Test
        @DisplayName("bitwise XOR written without whitespace parses (the ^ no longer starts an identifier)")
        void whenXorWrittenTightThenDocumentIsValid() {
            assertThat(DocumentCompiler.parseDocument("policy \"p\" permit 5^3 == 6;").isInvalid()).isFalse();
        }

        @Test
        @DisplayName("a keyword can be a variable name via backtick escape, and the reference resolves to it")
        void whenKeywordEscapedAsVariableThenDocumentIsValid() {
            assertThat(
                    DocumentCompiler.parseDocument("policy \"p\" permit var `permit` = 5; `permit` == 5;").isInvalid())
                    .isFalse();
        }

        @Test
        @DisplayName("a keyword-named object key is accessed via backtick escape")
        void whenKeywordKeyEscapedThenDocumentIsValid() {
            assertThat(DocumentCompiler.parseDocument("policy \"p\" permit {\"in\": 5}.`in` == 5;").isInvalid())
                    .isFalse();
        }
    }
}
