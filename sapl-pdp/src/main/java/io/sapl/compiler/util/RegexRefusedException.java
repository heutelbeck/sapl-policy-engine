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
package io.sapl.compiler.util;

import java.io.Serial;

import lombok.Getter;

/**
 * Thrown where an interface demands an exception for a regular expression
 * {@link LinearRegex} refuses, such as a {@code pattern} keyword of a JSON
 * Schema or a constant of the code.
 */
@Getter
public class RegexRefusedException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_PATTERN_REFUSED = "The regular expression '%s' is refused: %s.";

    /**
     * Why the pattern was refused, as {@link LinearRegex#describe} states it.
     */
    private final String description;

    /**
     * Creates the exception.
     *
     * @param pattern the refused pattern
     * @param description why the pattern was refused
     */
    public RegexRefusedException(String pattern, String description) {
        super(ERROR_PATTERN_REFUSED.formatted(pattern, description));
        this.description = description;
    }
}
