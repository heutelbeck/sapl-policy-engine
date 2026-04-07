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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

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
     * Compacts the equality/inequality constraints for a specific formula bucket.
     * Resolves NE formulas into concrete branch memberships.
     *
     * @param formulasInBucket the formulas currently in scope at this equality
     * level
     * @return the compacted result with per-branch formula sets, default set, and
     * affected set
     */
    CompactedBranches compact(BitSet formulasInBucket) {
        val branchFormulas        = new HashMap<Value, BitSet>();
        val unconstrainedFormulas = new BitSet();
        val allExcludeInBucket    = new BitSet();

        // EQ formulas per constant, intersected with bucket
        for (val entry : equalsFormulas.entrySet()) {
            val inBucket = (BitSet) entry.getValue().clone();
            inBucket.and(formulasInBucket);
            if (!inBucket.isEmpty()) {
                branchFormulas.put(entry.getKey(), inBucket);
            }
        }

        // All NE formulas in this bucket
        for (val entry : excludeFormulas.entrySet()) {
            val inBucket = (BitSet) entry.getValue().clone();
            inBucket.and(formulasInBucket);
            allExcludeInBucket.or(inBucket);
        }

        // Unconstrained: in bucket but not in any EQ or NE
        val allConstrained = new BitSet();
        for (val bits : branchFormulas.values()) {
            allConstrained.or(bits);
        }
        allConstrained.or(allExcludeInBucket);
        unconstrainedFormulas.or(formulasInBucket);
        unconstrainedFormulas.andNot(allConstrained);

        // Add NE formulas to each EQ branch where they're not excluded
        for (val entry : branchFormulas.entrySet()) {
            val branchValue = entry.getKey();
            val branchBits  = entry.getValue();
            for (val excludeEntry : excludeFormulas.entrySet()) {
                if (!excludeEntry.getKey().equals(branchValue)) {
                    val excludeInBucket = (BitSet) excludeEntry.getValue().clone();
                    excludeInBucket.and(formulasInBucket);
                    branchBits.or(excludeInBucket);
                }
            }
            branchBits.or(unconstrainedFormulas);
        }

        // Excluded keys without an EQ branch need an explicit empty branch
        for (val excludeEntry : excludeFormulas.entrySet()) {
            val excludedValue = excludeEntry.getKey();
            if (!branchFormulas.containsKey(excludedValue)) {
                // Value matches excludedValue: != excludedValue is FALSE.
                // But != otherValue IS satisfied, so add those formulas.
                val emptyBranchBits = (BitSet) unconstrainedFormulas.clone();
                for (val otherExclude : excludeFormulas.entrySet()) {
                    if (!otherExclude.getKey().equals(excludedValue)) {
                        val inBucket = (BitSet) otherExclude.getValue().clone();
                        inBucket.and(formulasInBucket);
                        emptyBranchBits.or(inBucket);
                    }
                }
                if (!emptyBranchBits.isEmpty()) {
                    branchFormulas.put(excludedValue, emptyBranchBits);
                }
            }
        }

        // Default: unconstrained + all NE formulas (unknown value satisfies all !=)
        val defaultFormulas = (BitSet) unconstrainedFormulas.clone();
        defaultFormulas.or(allExcludeInBucket);

        return new CompactedBranches(branchFormulas, defaultFormulas, unconstrainedFormulas, allConstrained);
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
