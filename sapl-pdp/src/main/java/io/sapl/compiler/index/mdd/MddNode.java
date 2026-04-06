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
package io.sapl.compiler.index.mdd;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import io.sapl.api.model.IndexPredicate;
import io.sapl.compiler.document.CompiledDocument;

/**
 * A node in the Multi-Valued Decision Diagram (MDD) policy index.
 * <p>
 * Each internal node represents a predicate evaluation with three outgoing
 * edges for the ternary outcome (true, false, error). Leaf nodes have no
 * predicate and no edges.
 * <p>
 * {@code matchedDocuments} contains documents whose formulas are fully
 * satisfied when arriving at this node. {@code errorDocuments} contains
 * documents whose formulas were affected by errors along the path.
 * {@code undecided} tracks formula indices still unresolved at this node,
 * used for DAG deduplication during construction.
 * <p>
 * Two nodes with the same predicate, matched, error, and undecided sets
 * are structurally identical (same subtree) due to the fixed predicate
 * order and deterministic partitioning. Children are ignored in
 * hashCode/equals because they are fully determined by these fields.
 * <p>
 * The diagram is immutable after construction. Evaluation is a stateless
 * traversal from root to leaf collecting results, inherently thread-safe.
 *
 * @param predicate the predicate to evaluate at this node (null for leaf)
 * @param trueChild child node when predicate evaluates to true
 * @param falseChild child node when predicate evaluates to false
 * @param errorChild child node when predicate evaluation errors
 * @param matchedDocuments documents whose formulas are fully satisfied
 * at this point in the traversal
 * @param errorDocuments documents whose formulas errored at this point
 * @param undecided formula indices still unresolved at this node
 */
public record MddNode(
        IndexPredicate predicate,
        MddNode trueChild,
        MddNode falseChild,
        MddNode errorChild,
        List<CompiledDocument> matchedDocuments,
        List<CompiledDocument> errorDocuments,
        BitSet undecided) {

    /** Shared empty leaf for terminal paths. */
    static final MddNode EMPTY_LEAF = new MddNode(null, null, null, null, List.of(), List.of(), new BitSet());

    /**
     * @return true if this is a leaf node (no predicate to evaluate)
     */
    public boolean isLeaf() {
        return predicate == null;
    }

    /**
     * Identity based on predicate, documents, and undecided set.
     * Children are ignored because they are fully determined by
     * these fields given the fixed predicate order.
     */
    @Override
    public int hashCode() {
        return Objects.hash(predicate, matchedDocuments, errorDocuments, undecided);
    }

    /**
     * Two nodes are equal if they have the same predicate, documents,
     * and undecided set. Children are guaranteed to be identical by
     * construction when these fields match.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MddNode other && Objects.equals(predicate, other.predicate)
                && Objects.equals(matchedDocuments, other.matchedDocuments)
                && Objects.equals(errorDocuments, other.errorDocuments) && Objects.equals(undecided, other.undecided));
    }

}
