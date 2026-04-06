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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.sapl.api.model.IndexPredicate;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.dnf.DisjunctiveFormula;
import io.sapl.compiler.index.dnf.Literal;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Builds an MDD policy index from DNF formulas at compile time.
 * <p>
 * Construction is a top-down DFS that partitions candidate conjunctions
 * at each predicate node. Structurally identical nodes are deduplicated
 * via a HashSet, producing a DAG rather than a tree. Two nodes are
 * identical when they have the same predicate, matched documents, error
 * documents, and undecided formula set (which determines the entire
 * subtree given the fixed predicate order).
 * <p>
 * No predicate evaluation happens during construction. The diagram
 * structure is derived entirely from the formula syntax.
 */
@Slf4j
@UtilityClass
class MddIndexBuilder {

    /**
     * A candidate tracks one conjunction from one formula. It records
     * which literals remain unsatisfied as the construction descends.
     */
    record Candidate(int formulaIndex, Set<Literal> remainingLiterals) {}

    /**
     * Builds the MDD from the given formulas and predicate order.
     *
     * @param predicateOrder predicates ordered by evaluation priority
     * @param formulas the DNF formulas to index
     * @param formulaDocuments documents per formula
     * @return the root node of the MDD
     */
    static MddNode build(List<IndexPredicate> predicateOrder, List<DisjunctiveFormula> formulas,
            List<List<CompiledDocument>> formulaDocuments) {
        val candidates = new ArrayList<Candidate>();
        for (var f = 0; f < formulas.size(); f++) {
            for (val clause : formulas.get(f).clauses()) {
                candidates.add(new Candidate(f, new HashSet<>(clause.literals())));
            }
        }

        log.info("MDD build: {} total candidates from {} formulas, {} predicates to process", candidates.size(),
                formulas.size(), predicateOrder.size());
        val dedup = new HashMap<MddNode, MddNode>();
        val root  = buildNode(predicateOrder, 0, candidates, new HashSet<>(), new HashSet<>(), formulaDocuments, dedup);
        log.info("MDD build: complete. {} unique nodes in DAG", dedup.size());
        return root;
    }

    private static MddNode buildNode(List<IndexPredicate> predicateOrder, int predicateIndex,
            List<Candidate> candidates, Set<Integer> matchedFormulas, Set<Integer> erroredFormulas,
            List<List<CompiledDocument>> formulaDocuments, HashMap<MddNode, MddNode> dedup) {

        val matchedDocs = collectDocuments(matchedFormulas, erroredFormulas, formulaDocuments);
        val errorDocs   = collectDocuments(erroredFormulas, formulaDocuments);
        val undecided   = candidateBitSet(candidates);

        // Base case: no more predicates or no more candidates
        if (predicateIndex >= predicateOrder.size() || candidates.isEmpty()) {
            val leaf = new MddNode(null, null, null, null, matchedDocs, errorDocs, undecided);
            return deduplicate(leaf, dedup);
        }

        val predicate = predicateOrder.get(predicateIndex);

        // Check if this predicate affects any candidate
        var predicateRelevant = false;
        for (val candidate : candidates) {
            for (val literal : candidate.remainingLiterals()) {
                if (literal.predicate().equals(predicate)) {
                    predicateRelevant = true;
                    break;
                }
            }
            if (predicateRelevant) {
                break;
            }
        }

        // Skip predicate if it doesn't affect any candidate
        if (!predicateRelevant) {
            return buildNode(predicateOrder, predicateIndex + 1, candidates, matchedFormulas, erroredFormulas,
                    formulaDocuments, dedup);
        }

        // Partition candidates by their relationship to this predicate
        val trueCandidates  = new ArrayList<Candidate>();
        val falseCandidates = new ArrayList<Candidate>();
        val errorCandidates = new ArrayList<Candidate>();

        val trueMatched  = new HashSet<>(matchedFormulas);
        val falseMatched = new HashSet<>(matchedFormulas);
        val errorErrored = new HashSet<>(erroredFormulas);

        for (val candidate : candidates) {
            Literal matchingLiteral = null;
            for (val literal : candidate.remainingLiterals()) {
                if (literal.predicate().equals(predicate)) {
                    matchingLiteral = literal;
                    break;
                }
            }

            if (matchingLiteral == null) {
                // Predicate not referenced: candidate survives all three branches
                trueCandidates.add(candidate);
                falseCandidates.add(candidate);
                errorCandidates.add(candidate);
            } else {
                val remaining = new HashSet<>(candidate.remainingLiterals());
                remaining.remove(matchingLiteral);

                errorErrored.add(candidate.formulaIndex());

                if (!matchingLiteral.negated()) {
                    // Positive literal: satisfied when predicate is true
                    if (remaining.isEmpty()) {
                        trueMatched.add(candidate.formulaIndex());
                    } else {
                        trueCandidates.add(new Candidate(candidate.formulaIndex(), remaining));
                    }
                    // False branch: conjunction eliminated
                    // Error branch: candidate survives with remaining literals
                    errorCandidates.add(new Candidate(candidate.formulaIndex(), remaining));
                } else {
                    // Negated literal: satisfied when predicate is false
                    if (remaining.isEmpty()) {
                        falseMatched.add(candidate.formulaIndex());
                    } else {
                        falseCandidates.add(new Candidate(candidate.formulaIndex(), remaining));
                    }
                    // True branch: conjunction eliminated
                    // Error branch: candidate survives with remaining literals
                    errorCandidates.add(new Candidate(candidate.formulaIndex(), remaining));
                }
            }
        }

        // Recurse: children are built first (DFS), matched/errored docs
        // are placed on the child where they resolve
        val trueChild  = buildNode(predicateOrder, predicateIndex + 1, trueCandidates, trueMatched, erroredFormulas,
                formulaDocuments, dedup);
        val falseChild = buildNode(predicateOrder, predicateIndex + 1, falseCandidates, falseMatched, erroredFormulas,
                formulaDocuments, dedup);
        val errorChild = buildNode(predicateOrder, predicateIndex + 1, errorCandidates, matchedFormulas, errorErrored,
                formulaDocuments, dedup);

        val node = new MddNode(predicate, trueChild, falseChild, errorChild, matchedDocs, errorDocs, undecided);
        return deduplicate(node, dedup);
    }

    private static MddNode deduplicate(MddNode node, HashMap<MddNode, MddNode> dedup) {
        val existing = dedup.get(node);
        if (existing != null) {
            return existing;
        }
        dedup.put(node, node);
        return node;
    }

    private static BitSet candidateBitSet(List<Candidate> candidates) {
        val bits = new BitSet();
        for (val c : candidates) {
            bits.set(c.formulaIndex());
        }
        return bits;
    }

    private static List<CompiledDocument> collectDocuments(Set<Integer> formulaIndices,
            List<List<CompiledDocument>> formulaDocuments) {
        if (formulaIndices.isEmpty()) {
            return List.of();
        }
        val docs = new ArrayList<CompiledDocument>();
        for (val f : formulaIndices) {
            docs.addAll(formulaDocuments.get(f));
        }
        return List.copyOf(docs);
    }

    private static List<CompiledDocument> collectDocuments(Set<Integer> formulaIndices, Set<Integer> exclude,
            List<List<CompiledDocument>> formulaDocuments) {
        if (formulaIndices.isEmpty()) {
            return List.of();
        }
        val docs = new ArrayList<CompiledDocument>();
        for (val f : formulaIndices) {
            if (!exclude.contains(f)) {
                docs.addAll(formulaDocuments.get(f));
            }
        }
        return List.copyOf(docs);
    }

}
