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

import io.sapl.api.coverage.BranchHit;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.model.Coverage.BodyCoverage;
import io.sapl.compiler.model.Coverage.ConditionHit;
import io.sapl.compiler.model.Coverage.DocumentCoverage;
import io.sapl.compiler.model.Coverage.PdpCoverage;
import io.sapl.compiler.model.Coverage.PolicyCoverage;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.model.Coverage.TargetResult;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String ERROR_NESTED_PDP = "PDP coverage cannot appear inside another document.";
    private static final String TYPE_POLICY      = "policy";
    private static final String TYPE_SET         = "set";

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

        if (docCoverage instanceof PdpCoverage pdpCoverage) {
            return extractFromContainedDocuments(pdpCoverage.documentCoverages(), vote, policySources);
        }

        // Legacy shape: PDP-level coverage encoded as a PolicySetCoverage with
        // PdpVoterMetadata. Retained until every producer emits PdpCoverage.
        if (docCoverage instanceof PolicySetCoverage psc && psc.voter() instanceof PdpVoterMetadata) {
            return extractFromContainedDocuments(psc.policyCoverages(), vote, policySources);
        }

        return docCoverage == null ? List.of() : List.of(extractDocumentCoverage(docCoverage, vote, policySources));
    }

    private static List<PolicyCoverageData> extractFromContainedDocuments(List<DocumentCoverage> documents, Vote vote,
            Map<String, String> policySources) {
        val childVotes = childVotes(vote);
        val results    = new ArrayList<PolicyCoverageData>(documents.size());
        for (val document : documents) {
            results.add(extractDocumentCoverage(document, childVotes.get(document.voter()), policySources));
        }
        return results;
    }

    private static Map<VoterMetadata, Vote> childVotes(@Nullable Vote parent) {
        val votes = new HashMap<VoterMetadata, Vote>();
        if (parent != null) {
            for (val child : parent.contributingVotes()) {
                votes.put(child.voter(), child);
            }
        }
        return votes;
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

    private static PolicyCoverageData extractDocumentCoverage(DocumentCoverage document, @Nullable Vote vote,
            Map<String, String> policySources) {
        val name = document.voter().name();
        val data = new PolicyCoverageData(name, policySources.getOrDefault(name, ""),
                document instanceof PolicySetCoverage ? TYPE_SET : TYPE_POLICY);
        recordDocument(document, vote, data);
        return data;
    }

    private static void recordDocument(DocumentCoverage document, @Nullable Vote vote, PolicyCoverageData data) {
        switch (document) {
        case PolicyCoverage policy -> {
            val conditional = recordBody(policy.bodyCoverage(), data);
            recordOutcome(vote, policy.declarationLocation(), conditional, data);
        }
        case PolicySetCoverage set -> {
            recordTarget(set.targetHit(), set.schemaHit(), data);
            if (set.schemaHit() != null) {
                recordCondition(set.schemaHit(), data);
            }
            recordOutcome(vote, set.declarationLocation(), false, data);
            val members = childVotes(vote);
            for (val member : set.policyCoverages()) {
                recordDocument(member, members.get(member.voter()), data);
            }
        }
        case PdpCoverage ignored   -> throw new IllegalStateException(ERROR_NESTED_PDP);
        }
    }

    private static boolean recordBody(@Nullable BodyCoverage body, PolicyCoverageData data) {
        if (body == null) {
            return false;
        }
        for (val hit : body.hits()) {
            recordCondition(hit, data);
        }
        return body.hasDecisionCondition();
    }

    private static void recordCondition(ConditionHit hit, PolicyCoverageData data) {
        if (hit.enforcedSchema() || (hit.statementId() >= 0 && hit.result() instanceof BooleanValue)) {
            recordResult(data, (int) hit.statementId(), hit.location(), hit.result(), hit.enforcedSchema());
        }
    }

    private static void recordTarget(Coverage.TargetHit target, @Nullable ConditionHit schema,
            PolicyCoverageData data) {
        switch (target) {
        case TargetResult(var match, var location) -> {
            recordTargetResult(match, data);
            // With no authored for-clause the schema is the effective target itself.
            if (location != null && location.line() > 0 && (schema == null || !location.equals(schema.location()))) {
                recordResult(data, -location.line(), location, match, false);
            }
        }
        case Coverage.BlankTargetHit ignored       -> {
            if (schema == null) {
                data.recordTargetHit(true);
            } else {
                recordTargetResult(schema.result(), data);
            }
        }
        case Coverage.NoTargetHit ignored          -> { /* The target was not reached. */ }
        }
    }

    private static void recordTargetResult(Value result, PolicyCoverageData data) {
        if (result instanceof BooleanValue(boolean matched)) {
            data.recordTargetHit(matched);
        } else {
            data.recordTargetError();
        }
    }

    private static void recordOutcome(@Nullable Vote vote, @Nullable SourceLocation declaration, boolean conditional,
            PolicyCoverageData data) {
        if (vote == null) {
            return;
        }
        val result      = switch (vote.authorizationDecision().decision()) {
                        case PERMIT, DENY, SUSPEND -> Value.TRUE;
                        case NOT_APPLICABLE        -> Value.FALSE;
                        case INDETERMINATE         -> Value.UNDEFINED;
                        };
        val statementId = conditional ? BranchHit.POLICY_TWO_BRANCH_ID : BranchHit.POLICY_SINGLE_BRANCH_ID;
        recordResult(data, statementId, declaration, result, false);
    }

    private static void recordResult(PolicyCoverageData data, int statementId, @Nullable SourceLocation location,
            Value result, boolean enforcedSchema) {
        int startLine = 0;
        int endLine   = 0;
        int startChar = 0;
        int endChar   = 0;
        if (location != null) {
            startLine = location.line();
            endLine   = Math.max(location.endLine(), startLine);
            startChar = Math.max(location.column() - 1, 0);
            endChar   = Math.max(location.endColumn() - 1, 0);
        } else if (statementId == BranchHit.POLICY_SINGLE_BRANCH_ID || statementId == BranchHit.POLICY_TWO_BRANCH_ID) {
            // Older producers supplied no declaration location.
            startLine = 1;
            endLine   = 1;
        }
        data.recordBranchHit(
                new BranchHit(statementId, startLine, endLine, startChar, endChar, Value.TRUE.equals(result) ? 1 : 0,
                        Value.FALSE.equals(result) ? 1 : 0, result instanceof BooleanValue ? 0 : 1, enforcedSchema));
    }
}
