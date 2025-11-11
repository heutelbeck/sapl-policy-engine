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
package io.sapl.api.shared;

public enum Match {
    NO_MATCH,
    EXACT_MATCH,
    VARARGS_MATCH,
    CATCH_ALL_MATCH;

    /**
     * Determines the priority level of this match type.
     * Higher values indicate more specific matches.
     *
     * @return the priority value (0-3)
     */
    private int priority() {
        return switch (this) {
        case NO_MATCH        -> 0;
        case CATCH_ALL_MATCH -> 1;
        case VARARGS_MATCH   -> 2;
        case EXACT_MATCH     -> 3;
        };
    }

    /**
     * Determines if this match type is more specific than another match type.
     * NO_MATCH is never better than any match type.
     * The specificity order is: EXACT_MATCH > VARARGS_MATCH > CATCH_ALL_MATCH >
     * NO_MATCH
     * <p/>
     * | this/other | NO_MATCH | EXACT_MATCH | VARARGS_MATCH | CATCH_ALL_MATCH |
     * |----------------|----------|-------------|---------------|-----------------|
     * | NO_MATCH | false | false | false | false |
     * | EXACT_MATCH | true | false | true | true |
     * | VARARGS_MATCH | true | false | false | true |
     * | CATCH_ALL_MATCH| true | false | false | false |
     *
     * @param other the match type to compare against
     * @return true if this match is more specific than the other match, false
     * otherwise
     */
    public boolean isBetterThan(Match other) {
        return this != NO_MATCH && priority() > other.priority();
    }
}
