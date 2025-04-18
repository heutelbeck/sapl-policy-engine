/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.validation;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Provides a pattern for checking syntactic correctness of names for policy
 * information points or functions.
 */
@UtilityClass
public class NameValidator {
    private static final String            REGEX   = "[a-zA-Z][a-zA-Z0-9]*\\.[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*){0,8}";
    private static final Predicate<String> PATTERN = Pattern.compile(REGEX).asMatchPredicate();

    /**
     * @param stringUnderTest a String for validation.
     * @throws IllegalArgumentException if the stringUnderTest does not match the
     * pattern for fully qualified names.
     */
    public static void requireValidName(String stringUnderTest) {
        if (!PATTERN.test(stringUnderTest)) {
            throw new IllegalArgumentException(String.format("""
                    The fully qualified name of a Policy Information Point or function must cosist of \
                    at least two Strings separated by a '.'. Each of the strings must not \
                    contain white spaces and must start with a letter. \
                    No special characters are allowed. \
                    At most ten segments are allowed. \
                    Name was: '%s'.""", stringUnderTest));
        }
    }
}
