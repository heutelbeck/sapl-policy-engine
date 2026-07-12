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
package io.sapl.compiler.index;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Collection;
import java.util.List;

/**
 * Reconciles index error-suspects with Kleene naive evaluation.
 * <p>
 * A short-circuiting boolean is order-independent, but an error is not detected
 * order-independently by the count-and-eliminate or diagram walk: whichever
 * predicate is evaluated first decides whether a clause errors or is dominated
 * by a sibling. Rather than reorder those well-tested cores, each backend
 * collects the documents whose applicability touched an error branch and hands
 * them here. Each suspect is re-evaluated directly, which is exactly what the
 * naive index does, so all backends agree on the error-meets-dominator corner.
 * The cost falls only on the rare error path. Clean matches and clean drops are
 * resolved by the index without re-evaluation.
 */
@UtilityClass
public class IndexReclassification {

    /**
     * Re-evaluates each suspect document's applicability under Kleene semantics.
     * A {@code true} result is added to {@code trueMatches}, an error becomes an
     * {@link PolicyMatches.ErrorMatch} carrying the document and its error, and
     * a dominating {@code false} is dropped.
     *
     * @param suspects documents whose index classification touched an error
     * @param ctx the evaluation context
     * @param trueMatches collects suspects that re-evaluate to applicable
     * @param errorMatches collects error matches for suspects that genuinely error
     */
    public static void reclassifySuspectsKleene(Collection<CompiledDocument> suspects, EvaluationContext ctx,
            List<CompiledDocument> trueMatches, List<PolicyMatches.ErrorMatch> errorMatches) {
        for (val document : suspects) {
            val applicability = applicability(document, ctx);
            if (applicability instanceof ErrorValue error) {
                errorMatches.add(new PolicyMatches.ErrorMatch(document, error));
            } else if (applicability instanceof BooleanValue(var b) && b) {
                trueMatches.add(document);
            }
        }
    }

    private static Value applicability(CompiledDocument document, EvaluationContext ctx) {
        val expression = document.isApplicable();
        if (expression instanceof Value value) {
            return value;
        }
        return ((PureOperator) expression).evaluate(ctx);
    }
}
