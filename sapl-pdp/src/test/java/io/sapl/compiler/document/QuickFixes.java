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

import io.sapl.compiler.diagnostics.QuickFix;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Collection;
import java.util.Comparator;

/**
 * Applies quick fixes to the text of a document, as an editor applies them.
 */
@UtilityClass
class QuickFixes {

    /**
     * The quick fixes are applied from the end of the document, so that the
     * regions of the others stay valid.
     *
     * @param document the text of a document
     * @param quickFixes quick fixes of its problems
     * @return the text with the quick fixes applied
     */
    static String applied(String document, Collection<QuickFix> quickFixes) {
        val repaired = new StringBuilder(document);
        quickFixes.stream().sorted(Comparator.comparingInt((QuickFix quickFix) -> quickFix.region().start()).reversed())
                .forEach(quickFix -> repaired.replace(quickFix.region().start(), quickFix.region().end(),
                        quickFix.replacement()));
        return repaired.toString();
    }

}
