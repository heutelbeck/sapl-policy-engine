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

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.compiler.diagnostics.Region;

import java.util.List;

/**
 * A problem that the classification of a refused token found, with the values
 * its texts name, before it is placed in the document.
 *
 * @param problem the problem
 * @param values the values its texts name
 */
record Found(Problem problem, List<String> values) {

    static Found of(Problem problem, String... values) {
        return new Found(problem, List.of(values));
    }

    /**
     * @return the problem, marking a region, without a quick fix
     */
    Diagnostic at(Region region) {
        return new Diagnostic(problem, values, region, List.of(), null);
    }

    /**
     * @return the problem, marking the refused token, without a quick fix
     */
    Diagnostic atRefused(Refusal refusal) {
        return at(refusal.refusedRegion());
    }

    /**
     * @return the problem, marking a region, with a quick fix
     */
    Diagnostic fixedBy(Region marked, QuickFix quickFix) {
        return new Diagnostic(problem, values, marked, List.of(), quickFix);
    }

    /**
     * @return the problem, marking a region, with the quick fix that replaces it
     */
    Diagnostic replacing(Region region, String replacement) {
        return fixedBy(region, new QuickFix(region, replacement));
    }

    /**
     * @return the problem, marking the refused token, with the quick fix that
     * replaces it
     */
    Diagnostic replacingRefused(Refusal refusal, String replacement) {
        return replacing(refusal.refusedRegion(), replacement);
    }

    /**
     * @return the problem, marking a region, with the quick fix that inserts what
     * is missing after it
     */
    Diagnostic insertingAfter(Refusal refusal, Region region, String missing) {
        return fixedBy(region, new QuickFix(Tokens.placeAt(region.end(), refusal.lines()), missing));
    }

}
