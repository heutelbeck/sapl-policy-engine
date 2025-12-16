/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.internal.TraceFields;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts coverage data from traced authorization decisions.
 * <p>
 * Traverses the trace structure produced by the PDP when running with
 * TraceLevel.COVERAGE to extract condition hits and target match information.
 */
@UtilityClass
public class CoverageExtractor {

    /**
     * Extracts coverage data from a TracedDecision.
     *
     * @param tracedDecision the traced decision containing coverage information
     * @param policySources map of policy names to their source code (for HTML
     * reporting)
     * @return list of policy coverage data extracted from the trace
     */
    public static List<PolicyCoverageData> extractCoverage(TracedDecision tracedDecision,
            Map<String, String> policySources) {
        return extractCoverage(tracedDecision.originalTrace(), policySources);
    }

    /**
     * Extracts coverage data from a traced PDP decision Value.
     *
     * @param tracedPdpDecision the traced decision Value
     * @param policySources map of policy names to their source code
     * @return list of policy coverage data for top-level documents only (inner
     * policies within sets are merged into their containing set)
     */
    public static List<PolicyCoverageData> extractCoverage(Value tracedPdpDecision, Map<String, String> policySources) {
        val result    = new ArrayList<PolicyCoverageData>();
        val documents = TracedPdpDecision.getDocuments(tracedPdpDecision);

        for (val document : documents) {
            val coverage = extractDocumentCoverage(document, policySources);
            if (coverage != null) {
                result.add(coverage);
            }
        }

        return result;
    }

    private static PolicyCoverageData extractDocumentCoverage(Value document, Map<String, String> policySources) {
        if (!(document instanceof ObjectValue docObj)) {
            return null;
        }

        val name = getTextValue(docObj, TraceFields.NAME);
        if (name == null) {
            return null;
        }

        val type         = getTextValue(docObj, TraceFields.TYPE);
        val source       = policySources.getOrDefault(name, "");
        val documentType = TraceFields.TYPE_SET.equals(type) ? "set" : "policy";

        val coverage = new PolicyCoverageData(name, source, documentType);

        // Extract target result for policies
        val targetResult = docObj.get(TraceFields.TARGET_RESULT);
        if (targetResult instanceof BooleanValue targetBool) {
            coverage.recordTargetHit(targetBool.value());
        } else {
            // Check target_match field (used for non-matching traces)
            val targetMatch = docObj.get(TraceFields.TARGET_MATCH);
            if (targetMatch instanceof BooleanValue matchBool) {
                coverage.recordTargetHit(matchBool.value());
            }
        }

        // Extract condition hits from this document
        val conditions = docObj.get(TraceFields.CONDITIONS);
        if (conditions instanceof ArrayValue conditionsArray) {
            for (val condition : conditionsArray) {
                extractConditionHit(condition, coverage);
            }
        }

        // For policy sets, merge coverage from nested policies into this set
        if (TraceFields.TYPE_SET.equals(type)) {
            var anyInnerPolicyMatched = false;
            val policies              = docObj.get(TraceFields.POLICIES);
            if (policies instanceof ArrayValue policiesArray) {
                for (val nestedPolicy : policiesArray) {
                    anyInnerPolicyMatched |= extractNestedPolicyCoverage(nestedPolicy, coverage);
                }
            }
            // Policy sets don't have explicit targets - they're "hit" if any inner policy
            // matched
            if (!coverage.wasTargetEvaluated() && anyInnerPolicyMatched) {
                coverage.recordTargetHit(true);
            } else if (!coverage.wasTargetEvaluated()) {
                // Set was evaluated but no inner policy matched
                val decision = getTextValue(docObj, TraceFields.DECISION);
                if (decision != null && !"NOT_APPLICABLE".equals(decision)) {
                    coverage.recordTargetHit(true);
                } else {
                    coverage.recordTargetHit(false);
                }
            }
        }

        return coverage;
    }

    /**
     * Extracts coverage from a nested policy and merges it into the containing set.
     * <p>
     * Inner policies share the same source file as their containing set, so their
     * condition hits are merged into the set's coverage data with correct line
     * numbers.
     *
     * @param nestedPolicy the nested policy trace object
     * @param setCoverage the coverage data for the containing policy set
     * @return true if the inner policy's target matched
     */
    private static boolean extractNestedPolicyCoverage(Value nestedPolicy, PolicyCoverageData setCoverage) {
        if (!(nestedPolicy instanceof ObjectValue policyObj)) {
            return false;
        }

        var targetMatched = false;

        // Check if target matched
        val targetResult = policyObj.get(TraceFields.TARGET_RESULT);
        if (targetResult instanceof BooleanValue targetBool) {
            targetMatched = targetBool.value();
        } else {
            val targetMatch = policyObj.get(TraceFields.TARGET_MATCH);
            if (targetMatch instanceof BooleanValue matchBool) {
                targetMatched = matchBool.value();
            }
        }

        // Extract condition hits and merge into set's coverage
        val conditions = policyObj.get(TraceFields.CONDITIONS);
        if (conditions instanceof ArrayValue conditionsArray) {
            for (val condition : conditionsArray) {
                extractConditionHit(condition, setCoverage);
            }
        }

        return targetMatched;
    }

    private static void extractConditionHit(Value condition, PolicyCoverageData coverage) {
        if (!(condition instanceof ObjectValue condObj)) {
            return;
        }

        val statementIdValue = condObj.get(TraceFields.STATEMENT_ID);
        val resultValue      = condObj.get(TraceFields.RESULT);
        val lineValue        = condObj.get(TraceFields.LINE);

        if (!(statementIdValue instanceof NumberValue statementIdNum)) {
            return;
        }
        if (!(resultValue instanceof BooleanValue resultBool)) {
            return;
        }

        val statementId = statementIdNum.value().intValue();
        val result      = resultBool.value();
        val line        = lineValue instanceof NumberValue lineNum ? lineNum.value().intValue() : 0;

        coverage.recordConditionHit(statementId, line, result);
    }

    private static String getTextValue(ObjectValue obj, String field) {
        val value = obj.get(field);
        if (value instanceof TextValue textValue) {
            return textValue.value();
        }
        return null;
    }

    /**
     * Checks if a traced decision contains coverage data.
     *
     * @param tracedDecision the decision to check
     * @return true if coverage data is present
     */
    public static boolean hasCoverageData(TracedDecision tracedDecision) {
        return hasCoverageData(tracedDecision.originalTrace());
    }

    /**
     * Checks if a traced PDP decision Value contains coverage data.
     *
     * @param tracedPdpDecision the decision Value to check
     * @return true if any document has conditions or target_result fields
     */
    public static boolean hasCoverageData(Value tracedPdpDecision) {
        val documents = TracedPdpDecision.getDocuments(tracedPdpDecision);
        for (val document : documents) {
            if (document instanceof ObjectValue docObj) {
                if (docObj.containsKey(TraceFields.CONDITIONS) || docObj.containsKey(TraceFields.TARGET_RESULT)) {
                    return true;
                }
            }
        }
        return false;
    }
}
