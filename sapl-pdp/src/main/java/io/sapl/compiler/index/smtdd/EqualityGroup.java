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
package io.sapl.compiler.index.smtdd;

import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.*;

/**
 * Accumulates equality/inequality predicates sharing the same PureOperator
 * operand during semantic analysis. After analysis, the group can compact
 * its constraints for a specific formula bucket, producing the concrete
 * branch-to-formulas mapping needed by the builder.
 */
@Getter
@RequiredArgsConstructor
public class EqualityGroup {

    private final PureOperator        sharedOperand;
    private final Map<Value, BitSet>  equalsFormulas      = new HashMap<>();
    private final Map<Value, BitSet>  excludeFormulas     = new HashMap<>();
    private final Set<IndexPredicate> tentativePredicates = new HashSet<>();

    void addEquals(Value constant, int formulaIndex, IndexPredicate predicate) {
        equalsFormulas.computeIfAbsent(constant, k -> new BitSet()).set(formulaIndex);
        tentativePredicates.add(predicate);
    }

    void addExclude(Value constant, int formulaIndex, IndexPredicate predicate) {
        excludeFormulas.computeIfAbsent(constant, k -> new BitSet()).set(formulaIndex);
        tentativePredicates.add(predicate);
    }

    int constantCount() {
        return equalsFormulas.size() + excludeFormulas.size();
    }

    /**
     * Compacts the equality/inequality constraints for a specific formula bucket
     * into the concrete branch-to-formulas mapping the builder needs.
     * <p>
     * A formula's equality ({@code ==}, {@code in}) and inequality ({@code !=})
     * constraints on this operand are intersected: a formula belongs in the branch
     * for value {@code v} only when {@code v} satisfies its equality constraint (it
     * has none, or {@code v} is one of its equality constants) and {@code v} is not
     * one of its excluded constants. A formula carrying any equality constraint can
     * never reach the default branch, since the default stands for an operand value
     * distinct from every named constant.
     *
     * @param formulasInBucket the formulas currently in scope at this equality
     * level
     * @return the compacted result with per-branch formula sets, default set, and
     * affected set
     */
    CompactedBranches compact(BitSet formulasInBucket) {
        val equalsInBucket  = intersectWithBucket(equalsFormulas, formulasInBucket);
        val excludeInBucket = intersectWithBucket(excludeFormulas, formulasInBucket);

        val equalityConstrained = union(equalsInBucket.values());
        val excludeConstrained  = union(excludeInBucket.values());

        // Formulas with no equality constraint on this operand satisfy the equality
        // test for any value, including a default value distinct from every constant.
        val equalityUnconstrained = (BitSet) formulasInBucket.clone();
        equalityUnconstrained.andNot(equalityConstrained);

        val branchValues = new HashSet<Value>();
        branchValues.addAll(equalsInBucket.keySet());
        branchValues.addAll(excludeInBucket.keySet());

        val branchFormulas = new HashMap<Value, BitSet>();
        for (val value : branchValues) {
            val members    = (BitSet) equalityUnconstrained.clone();
            val equalsHere = equalsInBucket.get(value);
            if (equalsHere != null) {
                members.or(equalsHere);
            }
            val excludedHere = excludeInBucket.get(value);
            if (excludedHere != null) {
                members.andNot(excludedHere);
            }
            // An exclusion-key value must own a branch even when empty, so the operand
            // routes there instead of falling through to default where its != formula
            // would wrongly count as matching.
            if (!members.isEmpty() || excludeInBucket.containsKey(value)) {
                branchFormulas.put(value, members);
            }
        }

        // Default branch: only equality-unconstrained formulas can match; their
        // exclusions are trivially satisfied by a value that is no named constant.
        val defaultFormulas = (BitSet) equalityUnconstrained.clone();

        // doesNotSplit treats a formula as unconstrained only when this operand
        // neither equals nor excludes anything for it.
        val unconstrainedFormulas = (BitSet) formulasInBucket.clone();
        unconstrainedFormulas.andNot(equalityConstrained);
        unconstrainedFormulas.andNot(excludeConstrained);

        // Every formula referencing this operand errors when the operand errors.
        val affectedFormulas = (BitSet) equalityConstrained.clone();
        affectedFormulas.or(excludeConstrained);

        return new CompactedBranches(branchFormulas, defaultFormulas, unconstrainedFormulas, affectedFormulas);
    }

    private static Map<Value, BitSet> intersectWithBucket(Map<Value, BitSet> source, BitSet formulasInBucket) {
        val inBucket = new HashMap<Value, BitSet>();
        for (val entry : source.entrySet()) {
            val bits = (BitSet) entry.getValue().clone();
            bits.and(formulasInBucket);
            if (!bits.isEmpty()) {
                inBucket.put(entry.getKey(), bits);
            }
        }
        return inBucket;
    }

    private static BitSet union(Collection<BitSet> sets) {
        val result = new BitSet();
        for (val bits : sets) {
            result.or(bits);
        }
        return result;
    }

    /**
     * Result of compacting an equality group for a specific formula bucket.
     *
     * @param branchFormulas per constant value, the formulas in that branch
     * @param defaultFormulas formulas for the default child (no constant matched)
     * @param unconstrainedFormulas formulas not constrained by this group
     * @param affectedFormulas all formulas that reference this operand (for error
     * tracking)
     */
    record CompactedBranches(
            Map<Value, BitSet> branchFormulas,
            BitSet defaultFormulas,
            BitSet unconstrainedFormulas,
            BitSet affectedFormulas) {

        boolean doesNotSplit(BitSet formulasInBucket) {
            return branchFormulas.size() <= 1 && unconstrainedFormulas.equals(formulasInBucket);
        }
    }

}
