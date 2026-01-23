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
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.model.Coverage.BodyCoverage;
import io.sapl.compiler.model.Coverage.ConditionHit;
import io.sapl.compiler.model.Coverage.DocumentCoverage;
import io.sapl.compiler.model.Coverage.PolicyCoverage;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.model.Coverage.TargetResult;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.VoteWithCoverage;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts coverage data from traced authorization decisions.
 * <p>
 * Traverses the typed coverage structure produced by the PDP when running with
 * coverage enabled to extract condition hits and target match information.
 */
@UtilityClass
public class CoverageExtractor {

    private static final String TYPE_POLICY = "policy";
    private static final String TYPE_SET    = "set";

    /**
     * Extracts coverage data from a VoteWithCoverage.
     *
     * @param voteWithCoverage the vote with coverage information
     * @param policySources map of policy names to their source code (for HTML
     * reporting)
     * @return list of policy coverage data for all contributing documents
     */
    public static List<PolicyCoverageData> extractCoverage(VoteWithCoverage voteWithCoverage,
            Map<String, String> policySources) {
        val docCoverage = voteWithCoverage.coverage();
        val vote        = voteWithCoverage.vote();

        // If the top-level coverage is from the PDP, descend into nested policy
        // coverages
        if (docCoverage instanceof PolicySetCoverage psc && psc.voter() instanceof PdpVoterMetadata) {
            val results = new ArrayList<PolicyCoverageData>();
            for (val nestedCoverage : psc.policyCoverages()) {
                val extracted = extractDocumentCoverage(nestedCoverage, vote, policySources);
                if (extracted != null) {
                    results.add(extracted);
                }
            }
            return results;
        }

        val coverage = extractDocumentCoverage(docCoverage, vote, policySources);
        return coverage != null ? List.of(coverage) : List.of();
    }

    /**
     * Checks if a VoteWithCoverage contains meaningful coverage data.
     *
     * @param voteWithCoverage the vote with coverage to check
     * @return true if coverage data is present
     */
    public static boolean hasCoverageData(VoteWithCoverage voteWithCoverage) {
        return voteWithCoverage.coverage() != null;
    }

    private static PolicyCoverageData extractDocumentCoverage(DocumentCoverage docCoverage, Vote vote,
            Map<String, String> policySources) {
        return switch (docCoverage) {
        case PolicyCoverage pc     -> extractPolicyCoverage(pc, vote, policySources);
        case PolicySetCoverage psc -> extractPolicySetCoverage(psc, vote, policySources);
        };
    }

    private static PolicyCoverageData extractPolicyCoverage(PolicyCoverage policyCoverage, Vote vote,
            Map<String, String> policySources) {
        val voter    = policyCoverage.voter();
        val name     = voter.name();
        val source   = policySources.getOrDefault(name, "");
        val coverage = new PolicyCoverageData(name, source, TYPE_POLICY);

        // Extract condition hits from body coverage
        extractBodyCoverage(policyCoverage.bodyCoverage(), coverage);

        // Record policy outcome
        recordPolicyOutcome(voter, vote, policyCoverage.bodyCoverage(), coverage);

        return coverage;
    }

    private static PolicyCoverageData extractPolicySetCoverage(PolicySetCoverage psCoverage, Vote vote,
            Map<String, String> policySources) {
        val voter    = psCoverage.voter();
        val name     = voter.name();
        val source   = policySources.getOrDefault(name, "");
        val coverage = new PolicyCoverageData(name, source, TYPE_SET);

        // Extract target hit for the policy set's "for" clause
        extractTargetHit(psCoverage.targetHit(), coverage);

        // Extract and merge nested policies' coverage into this set's coverage
        for (val nestedDoc : psCoverage.policyCoverages()) {
            extractNestedDocumentCoverage(nestedDoc, vote, coverage);
        }

        // Record policy set outcome
        recordPolicySetOutcome(voter, vote, coverage);

        return coverage;
    }

    /**
     * Extracts coverage from a nested document and merges it into the parent set's
     * coverage.
     * <p>
     * Nested policies share the same source file as their containing set, so their
     * coverage is merged into the set's PolicyCoverageData with correct line
     * numbers.
     */
    private static void extractNestedDocumentCoverage(DocumentCoverage nestedDoc, Vote vote,
            PolicyCoverageData setCoverage) {
        switch (nestedDoc) {
        case PolicyCoverage nestedPolicy -> {
            // Extract body coverage (conditions) into parent set
            extractBodyCoverage(nestedPolicy.bodyCoverage(), setCoverage);
            // Record nested policy outcome
            recordPolicyOutcome(nestedPolicy.voter(), vote, nestedPolicy.bodyCoverage(), setCoverage);
        }
        case PolicySetCoverage nestedSet -> {
            // Extract nested set's target
            extractTargetHit(nestedSet.targetHit(), setCoverage);
            // Flatten nested set's policies into parent (don't recurse deeper)
            for (val innerDoc : nestedSet.policyCoverages()) {
                extractNestedDocumentCoverage(innerDoc, vote, setCoverage);
            }
        }
        }
    }

    private static void extractTargetHit(Coverage.TargetHit targetHit, PolicyCoverageData coverage) {
        switch (targetHit) {
        case TargetResult tr when tr.location() != null -> {
            val matched   = Value.TRUE.equals(tr.match());
            val location  = tr.location();
            val startLine = location.line();
            val endLine   = location.endLine() > 0 ? location.endLine() : startLine;
            coverage.recordTargetHit(matched, startLine, endLine);
        }
        case TargetResult tr                            -> {
            // TargetResult without location - just record the match result
            val matched = Value.TRUE.equals(tr.match());
            coverage.recordTargetHit(matched);
        }
        case Coverage.BlankTargetHit ignored            -> {
            // Blank target (no "for" clause) - implicitly matches everything
            // Record as a target hit since blank target = always applicable
            coverage.recordTargetHit(true);
        }
        case Coverage.NoTargetHit ignored               -> {
            // Target was not evaluated (policy not reached)
            // Don't record anything
        }
        }
    }

    private static void extractBodyCoverage(BodyCoverage bodyCoverage, PolicyCoverageData coverage) {
        if (bodyCoverage == null) {
            return;
        }
        for (val hit : bodyCoverage.hits()) {
            extractConditionHit(hit, coverage);
        }
    }

    private static void extractConditionHit(ConditionHit hit, PolicyCoverageData coverage) {
        val result = hit.result();

        // Only record boolean results - skip errors
        if (!(result instanceof BooleanValue boolResult)) {
            return;
        }

        val statementId = (int) hit.statementId();
        val location    = hit.location();
        val boolValue   = boolResult.value();

        if (location != null) {
            val startLine = location.line();
            val endLine   = location.endLine() > 0 ? location.endLine() : startLine;
            val startChar = location.column() > 0 ? location.column() - 1 : 0; // Convert 1-based to 0-based
            val endChar   = location.endColumn() > 0 ? location.endColumn() - 1 : 0;
            coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, boolValue);
        } else {
            // No location - use line 0 as fallback
            coverage.recordConditionHit(statementId, 0, boolValue);
        }
    }

    /**
     * Records policy outcome for coverage tracking.
     * <p>
     * Determines whether the policy returned its entitlement (PERMIT/DENY) or
     * NOT_APPLICABLE, and records this for branch coverage.
     */
    private static void recordPolicyOutcome(VoterMetadata voter, Vote vote, BodyCoverage bodyCoverage,
            PolicyCoverageData coverage) {
        val outcome       = voter.outcome();
        val decision      = vote.authorizationDecision().decision();
        val hasConditions = bodyCoverage != null && bodyCoverage.numberOfConditions() > 0;

        // entitlementReturned: true if the actual decision matches the policy's
        // declared outcome
        val entitlementReturned = isEntitlementReturned(outcome, decision);

        // Use line 1 as default location for policy outcome (represents the policy as a
        // whole)
        coverage.recordPolicyOutcome(1, 1, 0, 0, entitlementReturned, hasConditions);
    }

    /**
     * Records policy set outcome for coverage tracking.
     */
    private static void recordPolicySetOutcome(VoterMetadata voter, Vote vote, PolicyCoverageData coverage) {
        val outcome  = voter.outcome();
        val decision = vote.authorizationDecision().decision();

        // Policy sets don't have conditions in the same sense, but we track their
        // outcome
        val entitlementReturned = isEntitlementReturned(outcome, decision);

        // Use line 1 as default location
        coverage.recordPolicyOutcome(1, 1, 0, 0, entitlementReturned, false);
    }

    /**
     * Determines if the actual decision matches the expected outcome (entitlement).
     */
    private static boolean isEntitlementReturned(Outcome outcome, Decision decision) {
        return switch (outcome) {
        case PERMIT         -> decision == Decision.PERMIT;
        case DENY           -> decision == Decision.DENY;
        case PERMIT_OR_DENY -> decision == Decision.PERMIT || decision == Decision.DENY;
        };
    }
}
