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
package io.sapl.attributes;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.sapl.grammar.antlr.SAPLLexer;
import lombok.val;

import static io.sapl.api.shared.NameValidator.requireValidName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Name validation agrees with SAPL lexer identifier characters")
class NameValidatorTests {

    @MethodSource("identifierCharacters")
    @ParameterizedTest(name = "{0}")
    void whenIdentifierContainsCharacterThenValidatorAgreesWithLexer(String description, String identifier) {
        val acceptedByLexer = isLexerIdentifier(identifier);

        assertThat(isValidName(identifier + ".member")).as("%s in namespace", description).isEqualTo(acceptedByLexer);
        assertThat(isValidName("library." + identifier)).as("%s in member", description).isEqualTo(acceptedByLexer);
    }

    static Stream<Arguments> identifierCharacters() {
        return IntStream.concat(IntStream.range(0, 128), IntStream.of(0x00E4, 0x03B1, 0x0430, 0x200B, 0x1F600))
                .filter(codePoint -> codePoint != '.') // Dots separate qualified name segments.
                .boxed().flatMap(codePoint -> {
                    val character = new String(Character.toChars(codePoint));
                    return Stream.of(arguments("U+%04X at start".formatted(codePoint), character + "identifier"),
                            arguments("U+%04X within identifier".formatted(codePoint),
                                    "identifier" + character + "suffix"));
                });
    }

    private static boolean isLexerIdentifier(String identifier) {
        val lexer = new SAPLLexer(CharStreams.fromString(identifier));
        lexer.removeErrorListeners();
        val token = lexer.nextToken();
        return token.getType() == SAPLLexer.ID && identifier.equals(token.getText())
                && lexer.nextToken().getType() == Token.EOF;
    }

    private static boolean isValidName(String name) {
        try {
            requireValidName(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

}
