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
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryPureValue;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryValuePure;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler.PureBooleanTypeCheck;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
                val eqInfo = extractEqualityInfo(predicate.operator());

                if (eqInfo != null) {
                    val operandHash = eqInfo.pureOperand().semanticHash();
                    val group       = groupsByOperandHash.computeIfAbsent(operandHash,
                            k -> new EqualityGroup(eqInfo.pureOperand()));

                    if (eqInfo.negated()) {
                        group.addExclude(eqInfo.constantValue(), formulaIndex, predicate);
                    } else {
                        group.addEquals(eqInfo.constantValue(), formulaIndex, predicate);
                    }
                } else {
                    ungroupablePredicateFormulas.computeIfAbsent(predicate, k -> new ArrayList<>()).add(formulaIndex);
                }
            }
        }

        // Phase 2: keep groups with 2+ distinct constants, push others back to
        // ungroupable
        val equalityGroups = new ArrayList<EqualityGroup>();
        for (val group : groupsByOperandHash.values()) {
            if (group.constantCount() >= 2) {
                equalityGroups.add(group);
            } else {
                for (val predicate : group.getTentativePredicates()) {
                    for (var formulaIndex = 0; formulaIndex < formulaPredicates.size(); formulaIndex++) {
                        if (formulaPredicates.get(formulaIndex).contains(predicate)) {
                            ungroupablePredicateFormulas.computeIfAbsent(predicate, k -> new ArrayList<>())
                                    .add(formulaIndex);
                        }
                    }
                }
            }
        }

        // Best discriminator first
        equalityGroups.sort(Comparator.comparingInt(EqualityGroup::constantCount).reversed());

        val remainingPredicates = new ArrayList<>(ungroupablePredicateFormulas.keySet());
        return new AnalysisResult(equalityGroups, remainingPredicates, ungroupablePredicateFormulas);
    }

    private record EqualityInfo(PureOperator pureOperand, Value constantValue, boolean negated) {}

    private static EqualityInfo extractEqualityInfo(PureOperator operator) {
        // Unwrap PureBooleanTypeCheck wrapper added by BooleanGuardCompiler
        if (operator instanceof PureBooleanTypeCheck typeCheck) {
            return extractEqualityInfo(typeCheck.operator());
        }
        if (operator instanceof BinaryPureValue pv
                && (pv.opType() == BinaryOperatorType.EQ || pv.opType() == BinaryOperatorType.NE)) {
            return new EqualityInfo(pv.lp(), pv.rv(), pv.opType() == BinaryOperatorType.NE);
        }
        if (operator instanceof BinaryValuePure vp
                && (vp.opType() == BinaryOperatorType.EQ || vp.opType() == BinaryOperatorType.NE)) {
            return new EqualityInfo(vp.rp(), vp.lv(), vp.opType() == BinaryOperatorType.NE);
        }
        return null;
    }

}
