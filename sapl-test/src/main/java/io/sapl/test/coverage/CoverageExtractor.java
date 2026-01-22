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
package io.sapl.test.coverage;

import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.model.*;
import io.sapl.api.pdp.traced.TraceFields;
import io.sapl.api.pdp.traced.TracedDecision;
import io.sapl.api.pdp.traced.TracedPdpDecision;
import io.sapl.compiler.pdp.VoteWithCoverage;
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
    public static List<PolicyCoverageData> extractCoverage(VoteWithCoverage tracedPdpDecision, Map<String, String> policySources) {
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
        val coverage     = new PolicyCoverageData(name, source, documentType);

        extractTargetCoverage(docObj, coverage);
        extractConditionsCoverage(docObj, coverage);
        extractPolicyOutcome(docObj, coverage);

        if (TraceFields.TYPE_SET.equals(type)) {
            extractPolicySetCoverage(docObj, coverage);
        }

        return coverage;
    }

    private static void extractTargetCoverage(ObjectValue docObj, PolicyCoverageData coverage) {
        val targetStartLine = getIntValue(docObj, TraceFields.TARGET_START_LINE);
        val targetEndLine   = getIntValue(docObj, TraceFields.TARGET_END_LINE);

        val targetResult = docObj.get(TraceFields.TARGET_RESULT);
        if (targetResult instanceof BooleanValue targetBool) {
            coverage.recordTargetHit(targetBool.value(), targetStartLine, targetEndLine);
            return;
        }

        val targetMatch = docObj.get(TraceFields.TARGET_MATCH);
        if (targetMatch instanceof BooleanValue matchBool) {
            coverage.recordTargetHit(matchBool.value(), targetStartLine, targetEndLine);
        }
    }

    private static void extractConditionsCoverage(ObjectValue docObj, PolicyCoverageData coverage) {
        val conditions = docObj.get(TraceFields.CONDITIONS);
        if (conditions instanceof ArrayValue conditionsArray) {
            for (val condition : conditionsArray) {
                extractConditionHit(condition, coverage);
            }
        }
    }

    private static void extractPolicySetCoverage(ObjectValue docObj, PolicyCoverageData coverage) {
        var anyInnerPolicyMatched = false;
        val policies              = docObj.get(TraceFields.POLICIES);
        if (policies instanceof ArrayValue policiesArray) {
            for (val nestedPolicy : policiesArray) {
                anyInnerPolicyMatched |= extractNestedPolicyCoverage(nestedPolicy, coverage);
            }
        }

        if (coverage.wasTargetEvaluated()) {
            return;
        }

        if (anyInnerPolicyMatched) {
            coverage.recordTargetHit(true);
        } else {
            val decision = getTextValue(docObj, TraceFields.DECISION);
            coverage.recordTargetHit(decision != null && !"NOT_APPLICABLE".equals(decision));
        }
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

        // Extract target position for nested policy
        val targetStartLine = getIntValue(policyObj, TraceFields.TARGET_START_LINE);
        val targetEndLine   = getIntValue(policyObj, TraceFields.TARGET_END_LINE);

        // Check if target matched and record with position
        val targetResult = policyObj.get(TraceFields.TARGET_RESULT);
        if (targetResult instanceof BooleanValue targetBool) {
            targetMatched = targetBool.value();
            // Record nested policy target hit into set's coverage for highlighting
            if (targetStartLine > 0) {
                setCoverage.recordTargetHit(targetMatched, targetStartLine, targetEndLine);
            }
        } else {
            val targetMatch = policyObj.get(TraceFields.TARGET_MATCH);
            if (targetMatch instanceof BooleanValue matchBool) {
                targetMatched = matchBool.value();
                if (targetStartLine > 0) {
                    setCoverage.recordTargetHit(targetMatched, targetStartLine, targetEndLine);
                }
            }
        }

        // Extract condition hits and merge into set's coverage
        val conditions = policyObj.get(TraceFields.CONDITIONS);
        if (conditions instanceof ArrayValue conditionsArray) {
            for (val condition : conditionsArray) {
                extractConditionHit(condition, setCoverage);
            }
        }

        // Extract policy outcome for nested policy
        extractPolicyOutcome(policyObj, setCoverage);

        return targetMatched;
    }

    /**
     * Extracts policy-level outcome for coverage tracking.
     * <p>
     * Determines whether the policy returned its entitlement (PERMIT/DENY) or
     * NOT_APPLICABLE, and records this for branch coverage. Policies without
     * conditions are single-branch (just need to be hit), while policies with
     * conditions are two-branch (need both outcomes for full coverage).
     *
     * @param policyObj the policy trace object
     * @param coverage the coverage data to update
     */
    private static void extractPolicyOutcome(ObjectValue policyObj, PolicyCoverageData coverage) {
        val policyStartLine = getIntValue(policyObj, TraceFields.POLICY_START_LINE);
        if (policyStartLine <= 0) {
            return; // No policy location data (coverage not enabled or constant-folded)
        }

        val policyEndLine   = getIntValue(policyObj, TraceFields.POLICY_END_LINE);
        val policyStartChar = getIntValue(policyObj, TraceFields.POLICY_START_CHAR);
        val policyEndChar   = getIntValue(policyObj, TraceFields.POLICY_END_CHAR);

        val hasConditionsValue = policyObj.get(TraceFields.HAS_CONDITIONS);
        val hasConditions      = hasConditionsValue instanceof BooleanValue bv && bv.value();

        val decision    = getTextValue(policyObj, TraceFields.DECISION);
        val entitlement = getTextValue(policyObj, TraceFields.ENTITLEMENT);

        // entitlementReturned: true if decision equals entitlement (PERMIT or DENY)
        // false if decision is NOT_APPLICABLE (conditions failed)
        val entitlementReturned = decision != null && decision.equals(entitlement);

        coverage.recordPolicyOutcome(policyStartLine, policyEndLine > 0 ? policyEndLine : policyStartLine,
                policyStartChar, policyEndChar, entitlementReturned, hasConditions);
    }

    private static void extractConditionHit(Value condition, PolicyCoverageData coverage) {
        if (!(condition instanceof ObjectValue condObj)) {
            return;
        }

        val statementIdValue = condObj.get(TraceFields.STATEMENT_ID);
        val resultValue      = condObj.get(TraceFields.RESULT);
        val startLineValue   = condObj.get(TraceFields.START_LINE);
        val endLineValue     = condObj.get(TraceFields.END_LINE);
        val startCharValue   = condObj.get(TraceFields.START_CHAR);
        val endCharValue     = condObj.get(TraceFields.END_CHAR);

        if (!(statementIdValue instanceof NumberValue statementIdNum)) {
            return;
        }
        if (!(resultValue instanceof BooleanValue resultBool)) {
            return;
        }

        val statementId = statementIdNum.value().intValue();
        val result      = resultBool.value();
        val startLine   = startLineValue instanceof NumberValue n ? n.value().intValue() : 0;
        val endLine     = endLineValue instanceof NumberValue n ? n.value().intValue() : startLine;
        val startChar   = startCharValue instanceof NumberValue n ? n.value().intValue() : 0;
        val endChar     = endCharValue instanceof NumberValue n ? n.value().intValue() : 0;

        coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, result);
    }

    private static String getTextValue(ObjectValue obj, String field) {
        val value = obj.get(field);
        if (value instanceof TextValue textValue) {
            return textValue.value();
        }
        return null;
    }

    private static int getIntValue(ObjectValue obj, String field) {
        val value = obj.get(field);
        if (value instanceof NumberValue numberValue) {
            return numberValue.value().intValue();
        }
        return 0;
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
            if (document instanceof ObjectValue docObj
                    && (docObj.containsKey(TraceFields.CONDITIONS) || docObj.containsKey(TraceFields.TARGET_RESULT))) {
                return true;
            }

        }
        return false;
    }
}
