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
package io.sapl.node.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

@DisplayName("SecretGenerator")
class SecretGeneratorTests {

    private static final int    REQUIRED_CHARACTERS_PER_CLASS = 2;
    private static final String SPECIAL_CHARACTERS            = "$-_.+!*'(),";

    @RepeatedTest(50)
    @DisplayName("generates basic-auth keys with the required length and base character classes")
    void whenGeneratingBasicKeyThenKeepsLengthAndBaseComposition() {
        assertBaseCredential(SecretGenerator.newKey(), SecretGenerator.BASIC_KEY_LENGTH);
    }

    @RepeatedTest(50)
    @DisplayName("generates API keys with the required length and base character classes")
    void whenGeneratingApiKeyThenKeepsLengthAndBaseComposition() {
        assertBaseCredential(SecretGenerator.newApiKey(), SecretGenerator.MIN_API_KEY_LENGTH);
    }

    @RepeatedTest(50)
    @DisplayName("generates basic-auth secrets with the required length and special characters")
    void whenGeneratingBasicSecretThenKeepsLengthAndSpecialComposition() {
        assertThat(SecretGenerator.newSecret()).hasSize(SecretGenerator.BASIC_SECRET_LENGTH)
                .satisfies(secret -> assertThat(countMatching(secret, SecretGeneratorTests::isAllowedSecretCharacter))
                        .isEqualTo(secret.length()))
                .satisfies(SecretGeneratorTests::assertBaseComposition)
                .satisfies(secret -> assertThat(countSpecialCharacters(secret))
                        .isGreaterThanOrEqualTo(REQUIRED_CHARACTERS_PER_CLASS));
    }

    private static void assertBaseCredential(String credential, int length) {
        assertThat(credential).hasSize(length)
                .satisfies(
                        value -> assertThat(countMatching(value, Character::isLetterOrDigit)).isEqualTo(value.length()))
                .satisfies(SecretGeneratorTests::assertBaseComposition);
    }

    private static void assertBaseComposition(String credential) {
        assertThat(countBaseCharacters(credential)).allSatisfy((characterClass, count) -> assertThat(count)
                .as(characterClass).isGreaterThanOrEqualTo(REQUIRED_CHARACTERS_PER_CLASS));
    }

    private static Map<String, Long> countBaseCharacters(String credential) {
        return Map.of("lowercase", countMatching(credential, Character::isLowerCase), "uppercase",
                countMatching(credential, Character::isUpperCase), "digit",
                countMatching(credential, Character::isDigit));
    }

    private static long countMatching(String credential, IntPredicate predicate) {
        return credential.chars().filter(predicate).count();
    }

    private static long countSpecialCharacters(String credential) {
        return countMatching(credential, character -> SPECIAL_CHARACTERS.indexOf(character) >= 0);
    }

    private static boolean isAllowedSecretCharacter(int character) {
        return Character.isLetterOrDigit(character) || SPECIAL_CHARACTERS.indexOf(character) >= 0;
    }

}
