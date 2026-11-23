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
import lombok.AccessLevel;
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

    private final PureOperator sharedOperand;
    // Linked in the order of the formulas: the hash of a null, undefined or error
    // constant is an identity hash, so hash order would change the branch order of
    // the built index from one run to the next.
    private final Map<Value, BitSet>  equalsFormulas      = new LinkedHashMap<>();
    private final Map<Value, BitSet>  excludeFormulas     = new LinkedHashMap<>();
    private final Set<IndexPredicate> tentativePredicates = new HashSet<>();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Set<Value>> allowedValuesByFormula = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private final BitSet                   formulasWithEquality   = new BitSet();

    @Getter(AccessLevel.NONE)
    private final BitSet formulasUsingKeys = new BitSet();

    void addKeys(Collection<Value> keys, int formulaIndex, IndexPredicate predicate) {
        formulasUsingKeys.set(formulaIndex);
        addAlternatives(keys, formulaIndex, predicate);
    }

    void addEquals(Value constant, int formulaIndex, IndexPredicate predicate) {
        addAlternatives(List.of(constant), formulaIndex, predicate);
    }

    // Values within a single membership predicate are alternatives. Separate
    // predicates on the same operand in one conjunction must all hold.
    void addAlternatives(Collection<Value> alternatives, int formulaIndex, IndexPredicate predicate) {
        tentativePredicates.add(predicate);
        val previous = allowedValuesByFormula.get(formulaIndex);
        if (previous == null) {
            val allowed = new LinkedHashSet<>(alternatives);
            allowedValuesByFormula.put(formulaIndex, allowed);
            formulasWithEquality.set(formulaIndex);
            for (val value : allowed) {
                equalsFormulas.computeIfAbsent(value, ignored -> new BitSet()).set(formulaIndex);
            }
            return;
        }
        val accepted = new HashSet<>(alternatives);
        previous.removeIf(value -> {
            if (accepted.contains(value)) {
                return false;
            }
            equalsFormulas.get(value).clear(formulaIndex);
            return true;
        });
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

        // Empty intersections still constrain the formula. They match no ordinary
        // branch, including the default, and still participate in error handling.
        val equalityConstrained = (BitSet) formulasWithEquality.clone();
        equalityConstrained.and(formulasInBucket);
        val excludeConstrained = union(excludeInBucket.values());

        // Formulas with no equality constraint on this operand satisfy the equality
        // test for any value, including a default value distinct from every constant.
        val equalityUnconstrained = (BitSet) formulasInBucket.clone();
        equalityUnconstrained.andNot(equalityConstrained);

        val branchValues = new LinkedHashSet<Value>();
        branchValues.addAll(equalsInBucket.keySet());
        branchValues.addAll(excludeInBucket.keySet());

        val branchFormulas = new LinkedHashMap<Value, BitSet>();
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

        // Default branch: only equality-unconstrained formulas can match. Their
        // exclusions are trivially satisfied by a value that is no named constant.
        // equalityUnconstrained is not read after this point, so reuse it directly.
        val defaultFormulas = equalityUnconstrained;

        // doesNotSplit treats a formula as unconstrained only when this operand
        // neither equals nor excludes anything for it.
        val unconstrainedFormulas = (BitSet) formulasInBucket.clone();
        unconstrainedFormulas.andNot(equalityConstrained);
        unconstrainedFormulas.andNot(excludeConstrained);

        // Every formula referencing this operand errors when the operand errors.
        // equalityConstrained is not read after this point, so consume it in place.
        equalityConstrained.or(excludeConstrained);
        val affectedFormulas = equalityConstrained;

        val keyFormulasInBucket = (BitSet) formulasUsingKeys.clone();
        keyFormulasInBucket.and(formulasInBucket);
        return new CompactedBranches(branchFormulas, defaultFormulas, unconstrainedFormulas, affectedFormulas,
                keyFormulasInBucket);
    }

    private static Map<Value, BitSet> intersectWithBucket(Map<Value, BitSet> source, BitSet formulasInBucket) {
        val inBucket = new LinkedHashMap<Value, BitSet>();
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
     * @param keyFormulas formulas using the operand as an object key
     */
    record CompactedBranches(
            Map<Value, BitSet> branchFormulas,
            BitSet defaultFormulas,
            BitSet unconstrainedFormulas,
            BitSet affectedFormulas,
            BitSet keyFormulas) {

        boolean doesNotSplit(BitSet formulasInBucket) {
            return branchFormulas.size() <= 1 && unconstrainedFormulas.equals(formulasInBucket);
        }
    }

}
