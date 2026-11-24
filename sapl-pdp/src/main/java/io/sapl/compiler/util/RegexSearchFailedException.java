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
 * Thrown where an interface demands an exception for a text that a regular
 * expression of {@link LinearRegex} cannot search, because it is longer than
 * the text limit or holds an unpaired surrogate. The text is unknown, never
 * a non-match, so that a negated pattern cannot let it through.
 */
@Getter
public class RegexSearchFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_SEARCH_FAILED = "Matching the regular expression '%s' failed: %s.";

    /**
     * The pattern that could not search the text.
     */
    private final String pattern;

    /**
     * Why the search failed, as {@link LinearRegex#describe} states it.
     */
    private final String description;

    /**
     * Creates the exception.
     *
     * @param pattern the pattern
     * @param description why the search failed
     */
    public RegexSearchFailedException(String pattern, String description) {
        super(ERROR_SEARCH_FAILED.formatted(pattern, description));
        this.pattern     = pattern;
        this.description = description;
    }
}
