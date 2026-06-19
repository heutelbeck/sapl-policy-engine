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

import io.sapl.api.model.EvaluationContext;

import java.util.function.Predicate;

/**
 * Determines which compiled documents are applicable for a given authorization
 * subscription.
 * <p>
 * Replaces the linear applicability evaluation loop in PDP-level combining
 * algorithms. Implementations range from naive linear scan
 * ({@link io.sapl.compiler.index.naive.NaivePolicyIndex}) to the
 * count-and-eliminate algorithm
 * ({@link io.sapl.compiler.index.canonical.CanonicalPolicyIndex}).
 */
public interface PolicyIndex {

    /**
     * Finds all documents applicable to the given evaluation context under Kleene
     * strong three-valued semantics. Returns the applicable documents split by
     * applicability outcome: TRUE matches and error matches, each error match
     * carrying its error. An erroring document is a candidate rather than a
     * terminal vote, so the combining algorithm can compose it with the document's
     * streaming section under Kleene strong three-valued AND.
     *
     * @param ctx the evaluation context containing the authorization subscription
     * @return documents whose applicability evaluated to TRUE or to an error
     */
    PolicyMatches matchKleene(EvaluationContext ctx);

    /**
     * Incremental variant of {@link #matchKleene(EvaluationContext)}. After each
     * step the newly classified matches are passed to {@code shouldContinue};
     * returning {@code false} stops evaluation immediately.
     *
     * @param ctx the evaluation context containing the authorization subscription
     * @param shouldContinue predicate called after each step with incremental
     * matches; returns false to stop
     */
    void matchKleeneWhile(EvaluationContext ctx, Predicate<PolicyMatches> shouldContinue);

}
