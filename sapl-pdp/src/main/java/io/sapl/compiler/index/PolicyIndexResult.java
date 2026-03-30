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

import java.util.List;

import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;

/**
 * Result of a policy index lookup. Contains the documents whose applicability
 * formulas matched the current authorization subscription, plus any error votes
 * produced when predicates failed during evaluation.
 * <p>
 * The combining algorithm consumes this by first folding error votes into its
 * accumulator, then evaluating voters for matching documents.
 *
 * @param matchingDocuments documents whose applicability evaluated to true
 * @param errorVotes votes produced by predicate evaluation errors
 */
public record PolicyIndexResult(List<CompiledDocument> matchingDocuments, List<Vote> errorVotes) {

    /**
     * Creates an index result with defensive copies.
     *
     * @param matchingDocuments documents whose applicability evaluated to true
     * @param errorVotes votes produced by predicate evaluation errors
     */
    public PolicyIndexResult(List<CompiledDocument> matchingDocuments, List<Vote> errorVotes) {
        this.matchingDocuments = List.copyOf(matchingDocuments);
        this.errorVotes        = List.copyOf(errorVotes);
    }

}
