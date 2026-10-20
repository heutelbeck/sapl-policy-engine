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
package io.sapl.api.shared;

import lombok.experimental.UtilityClass;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Validates fully qualified names of attributes and functions. Each segment uses
 * the SAPL lexer identifier characters: ASCII letters, underscores, or dollar
 * signs, followed by those characters or digits.
 */
@UtilityClass
public class NameValidator {

    private static final String ERROR_INVALID_NAME = """
            The fully qualified name of an attribute or function must consist of \
            two to ten segments separated by '.'. Each segment must start with an \
            ASCII letter, '_' or '$', followed by ASCII letters, digits, '_' or '$'. \
            Name was: '%s'.""";

    private static final String            REGEX   = "[a-zA-Z_$][a-zA-Z_$0-9]*(\\.[a-zA-Z_$][a-zA-Z_$0-9]*){1,9}";
    private static final Predicate<String> PATTERN = Pattern.compile(REGEX).asMatchPredicate();

    /**
     * @param stringUnderTest a String for validation.
     * @throws IllegalArgumentException if the stringUnderTest does not match the
     * pattern for fully qualified names.
     */
    public static void requireValidName(String stringUnderTest) {
        if (stringUnderTest == null || !PATTERN.test(stringUnderTest)) {
            throw new IllegalArgumentException(ERROR_INVALID_NAME.formatted(stringUnderTest));
        }
    }
}
