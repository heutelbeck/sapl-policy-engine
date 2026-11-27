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
package io.sapl.compiler.diagnostics;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A problem found in a SAPL document, with what an editor needs to show it.
 *
 * @param problem what is wrong
 * @param values the values of this occurrence that the texts of the problem
 * name, in the order the problem states
 * @param region the region of the document to mark
 * @param furtherRegions further regions that help to understand the problem
 * @param quickFix a change to the document that resolves the problem, or null
 * if there is none
 */
public record Diagnostic(
        Problem problem,
        List<String> values,
        Region region,
        List<FurtherRegion> furtherRegions,
        @Nullable QuickFix quickFix) {

    private static final String ERROR_NUMBER_OF_VALUES = "The texts of the problem %s name %d values. Given were %d.";

    /**
     * @throws IllegalArgumentException if the number of values is not the number
     * that the texts of the problem name
     */
    public Diagnostic {
        values         = List.copyOf(values);
        furtherRegions = List.copyOf(furtherRegions);
        if (values.size() != problem.numberOfValues()) {
            throw new IllegalArgumentException(
                    ERROR_NUMBER_OF_VALUES.formatted(problem, problem.numberOfValues(), values.size()));
        }
    }

    /**
     * @return the identifier of the class of the problem, which is the same in
     * all implementations of SAPL
     */
    public String identifier() {
        return problem.identifier();
    }

    /**
     * @return what is wrong, in a few words
     */
    public String shortForm() {
        return problem.shortForm(values);
    }

    /**
     * @return what to do about the problem
     */
    public String explanation() {
        return problem.explanation(values);
    }

    /**
     * @return the short form followed by the explanation
     */
    public String message() {
        return shortForm() + " " + explanation();
    }

}
