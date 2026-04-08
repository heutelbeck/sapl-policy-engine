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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryPureValue;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryValuePure;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler.PureBooleanTypeCheck;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Analyzes predicates for semantic grouping opportunities.
 * <p>
 * Identifies equality predicates that share a common PureOperator
 * operand (e.g., {@code subject.role == "doctor"},
 * {@code subject.role == "nurse"}).
 * These can be collapsed into a single multi-way branch node that
 * evaluates the shared operand once and looks up the constant in a HashMap.
 * <p>
 * Remaining predicates (non-equality, PureOperator==PureOperator, or
 * ungroupable) are kept as individual binary predicates.
 */
@UtilityClass
public class SemanticVariableOrder {

    /**
     * Result of semantic analysis: equality groups + remaining binary predicates.
     *
     * @param equalityGroups groups of predicates sharing a PureOperator, sorted by
     * total formula count descending
     * @param remainingPredicates predicates that could not be grouped
     * @param formulasPerRemainingPredicate formula indices per remaining predicate
     */
    public record AnalysisResult(
            List<EqualityGroup> equalityGroups,
            List<IndexPredicate> remainingPredicates,
            Map<IndexPredicate, List<Integer>> formulasPerRemainingPredicate) {

        /**
         * Renders a human-readable summary of the analysis for diagnostic logging.
         *
         * @return multi-line analysis report
         */
        String toAnalysisReport() {
            val report = new StringBuilder();
            report.append("Semantic analysis: ").append(equalityGroups.size()).append(" equality groups\n");
            for (var i = 0; i < equalityGroups.size(); i++) {
                val group = equalityGroups.get(i);
                report.append("  Group ").append(i).append(": hash=").append(group.getSharedOperand().semanticHash())
                        .append(", ").append(group.getEqualsFormulas().size()).append(" == branches, ")
                        .append(group.getExcludeFormulas().size()).append(" != branches, ")
                        .append(group.constantCount()).append(" distinct constants\n");
            }
            report.append("Remaining binary predicates: ").append(remainingPredicates.size()).append('\n');
            for (val pred : remainingPredicates) {
                val count     = formulasPerRemainingPredicate.get(pred).size();
                val innerType = unwrapInnerType(pred.operator());
                report.append("  predicate hash=").append(pred.semanticHash()).append(" -> ").append(count)
                        .append(" formulas (").append(innerType).append(")\n");
            }
            return report.toString();
        }

        private static String unwrapInnerType(PureOperator operator) {
            if (operator instanceof PureBooleanTypeCheck typeCheck) {
                return typeCheck.operator().getClass().getSimpleName();
            }
            return operator.getClass().getSimpleName();
        }
    }

    /**
     * Analyzes the predicates from all formulas and groups equality predicates
     * by shared operand.
     *
     * @param formulaPredicates for each formula index, the predicates it references
     * @return the analysis result with equality groups and remaining predicates
     */
    public static AnalysisResult analyze(List<List<IndexPredicate>> formulaPredicates) {
        val groupsByOperandHash          = new HashMap<Long, EqualityGroup>();
        val ungroupablePredicateFormulas = new HashMap<IndexPredicate, List<Integer>>();

        // Phase 1: classify each predicate into its equality group or ungroupable
        for (var formulaIndex = 0; formulaIndex < formulaPredicates.size(); formulaIndex++) {
            for (val predicate : formulaPredicates.get(formulaIndex)) {
                if (!classifyIntoGroup(predicate, formulaIndex, groupsByOperandHash)) {
                    ungroupablePredicateFormulas.computeIfAbsent(predicate, k -> new ArrayList<>()).add(formulaIndex);
                }
            }
        }

        // Phase 2: keep groups with 2+ distinct constants, push small groups back
        val equalityGroups = pruneSmallGroups(groupsByOperandHash, formulaPredicates, ungroupablePredicateFormulas);
        equalityGroups.sort(Comparator.comparingInt(EqualityGroup::constantCount).reversed());

        val remainingPredicates = new ArrayList<>(ungroupablePredicateFormulas.keySet());
        return new AnalysisResult(equalityGroups, remainingPredicates, ungroupablePredicateFormulas);
    }

    private static ArrayList<EqualityGroup> pruneSmallGroups(Map<Long, EqualityGroup> groupsByOperandHash,
            List<List<IndexPredicate>> formulaPredicates, Map<IndexPredicate, List<Integer>> ungroupable) {
        val equalityGroups = new ArrayList<EqualityGroup>();
        for (val group : groupsByOperandHash.values()) {
            if (group.constantCount() >= 2) {
                equalityGroups.add(group);
            } else {
                pushBackToUngroupable(group, formulaPredicates, ungroupable);
            }
        }
        return equalityGroups;
    }

    private static void pushBackToUngroupable(EqualityGroup group, List<List<IndexPredicate>> formulaPredicates,
            Map<IndexPredicate, List<Integer>> ungroupable) {
        for (val predicate : group.getTentativePredicates()) {
            for (var formulaIndex = 0; formulaIndex < formulaPredicates.size(); formulaIndex++) {
                if (formulaPredicates.get(formulaIndex).contains(predicate)) {
                    ungroupable.computeIfAbsent(predicate, k -> new ArrayList<>()).add(formulaIndex);
                }
            }
        }
    }

    /**
     * Attempts to classify a predicate into an equality group. Returns true
     * if the predicate was grouped, false if it should be treated as a
     * remaining binary predicate.
     */
    private static boolean classifyIntoGroup(IndexPredicate predicate, int formulaIndex,
            Map<Long, EqualityGroup> groupsByOperandHash) {
        val operator = unwrapTypeCheck(predicate.operator());
        if (operator instanceof BinaryPureValue pv) {
            return classifyBinaryPureValue(pv, pv.lp(), pv.rv(), predicate, formulaIndex, groupsByOperandHash);
        }
        if (operator instanceof BinaryValuePure vp) {
            return classifyBinaryValuePure(vp, vp.rp(), vp.lv(), predicate, formulaIndex, groupsByOperandHash);
        }
        return false;
    }

    private static boolean classifyBinaryPureValue(BinaryPureValue op, PureOperator pureOperand, Value constant,
            IndexPredicate predicate, int formulaIndex, Map<Long, EqualityGroup> groupsByOperandHash) {
        return switch (op.opType()) {
        case EQ -> addSingleConstant(pureOperand, constant, false, predicate, formulaIndex, groupsByOperandHash);
        case NE -> addSingleConstant(pureOperand, constant, true, predicate, formulaIndex, groupsByOperandHash);
        case IN -> addInConstants(pureOperand, constant, predicate, formulaIndex, groupsByOperandHash);
        default -> false;
        };
    }

    private static boolean classifyBinaryValuePure(BinaryValuePure op, PureOperator pureOperand, Value constant,
            IndexPredicate predicate, int formulaIndex, Map<Long, EqualityGroup> groupsByOperandHash) {
        // Note: for ValuePure, pureOperand=rp and constant=lv (swapped from source
        // order)
        // IN: constant IN pureOperator means collection is dynamic - not groupable
        // HAS_ONE: constant has pureOperator means "is pureOp's result a key of
        // constant?" - groupable
        return switch (op.opType()) {
        case EQ      -> addSingleConstant(pureOperand, constant, false, predicate, formulaIndex, groupsByOperandHash);
        case NE      -> addSingleConstant(pureOperand, constant, true, predicate, formulaIndex, groupsByOperandHash);
        case HAS_ONE -> addHasConstants(pureOperand, constant, predicate, formulaIndex, groupsByOperandHash);
        default      -> false;
        };
    }

    private static boolean addSingleConstant(PureOperator pureOperand, Value constant, boolean negated,
            IndexPredicate predicate, int formulaIndex, Map<Long, EqualityGroup> groupsByOperandHash) {
        val group = groupsByOperandHash.computeIfAbsent(pureOperand.semanticHash(),
                k -> new EqualityGroup(pureOperand));
        if (negated) {
            group.addExclude(constant, formulaIndex, predicate);
        } else {
            group.addEquals(constant, formulaIndex, predicate);
        }
        return true;
    }

    private static boolean addHasConstants(PureOperator pureOperand, Value container, IndexPredicate predicate,
            int formulaIndex, Map<Long, EqualityGroup> groupsByOperandHash) {
        // CONST has PureOp: the operator evaluates to a key, check against constant's
        // keys
        if (container instanceof ObjectValue object) {
            val group = groupsByOperandHash.computeIfAbsent(pureOperand.semanticHash(),
                    k -> new EqualityGroup(pureOperand));
            for (val key : object.keySet()) {
                group.addEquals(Value.of(key), formulaIndex, predicate);
            }
            return true;
        }
        return false;
    }

    private static boolean addInConstants(PureOperator pureOperand, Value collection, IndexPredicate predicate,
            int formulaIndex, Map<Long, EqualityGroup> groupsByOperandHash) {
        if (collection instanceof ArrayValue array) {
            val group = groupsByOperandHash.computeIfAbsent(pureOperand.semanticHash(),
                    k -> new EqualityGroup(pureOperand));
            for (var i = 0; i < array.size(); i++) {
                group.addEquals(array.get(i), formulaIndex, predicate);
            }
            return true;
        }
        if (collection instanceof ObjectValue object) {
            val group = groupsByOperandHash.computeIfAbsent(pureOperand.semanticHash(),
                    k -> new EqualityGroup(pureOperand));
            for (val value : object.values()) {
                group.addEquals(value, formulaIndex, predicate);
            }
            return true;
        }
        return false;
    }

    private static PureOperator unwrapTypeCheck(PureOperator operator) {
        if (operator instanceof PureBooleanTypeCheck typeCheck) {
            return typeCheck.operator();
        }
        return operator;
    }

}
