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

import java.util.function.Predicate;

import io.sapl.api.model.EvaluationContext;

/**
 * Determines which compiled documents are applicable for a given authorization
 * subscription.
 * <p>
 * Replaces the linear applicability evaluation loop in PDP-level combining
 * algorithms. Implementations range from naive linear scan
 * ({@link NaivePolicyIndex}) to the count-and-eliminate algorithm
 * ({@link io.sapl.compiler.index.canonical.CanonicalPolicyIndex}).
 */
public interface PolicyIndex {

    /**
     * Finds all documents applicable to the given evaluation context.
     *
     * @param ctx the evaluation context containing the authorization subscription
     * @return matching documents and any error votes from predicate evaluation
     */
    PolicyIndexResult match(EvaluationContext ctx);

    /**
     * Incrementally finds applicable documents, stopping when the consumer
     * signals completion. After each evaluation step, newly matched documents
     * and error votes are passed to {@code shouldContinue}. If it returns
     * {@code false}, evaluation stops immediately.
     *
     * @param ctx the evaluation context containing the authorization subscription
     * @param shouldContinue predicate called after each step with incremental
     * results; returns false to stop
     */
    void matchWhile(EvaluationContext ctx, Predicate<PolicyIndexResult> shouldContinue);

}
