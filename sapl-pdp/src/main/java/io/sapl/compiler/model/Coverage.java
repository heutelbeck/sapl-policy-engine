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
package io.sapl.compiler.model;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.VoterMetadata;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record Coverage(List<DocumentCoverage> coverage) {

    public static final TargetHit NO_TARGET_HIT = new Coverage.NoTargetHit();
    public static final TargetHit BLANK_TARGET_HIT = new Coverage.BlankTargetHit();

    public sealed interface DocumentCoverage permits PolicyCoverage, PolicySetCoverage, PdpCoverage {
        VoterMetadata voter();
    }

    /**
     * @param voter policy that produced the vote
     * @param bodyCoverage observations of the reached conditions
     * @param declarationLocation location of the policy keyword, if available
     */
    public record PolicyCoverage(VoterMetadata voter, BodyCoverage bodyCoverage, SourceLocation declarationLocation)
            implements DocumentCoverage {
        public PolicyCoverage(VoterMetadata voter, BodyCoverage bodyCoverage) {
            this(voter, bodyCoverage, null);
        }
    }

    /**
     * @param voter the policy set
     * @param targetHit observation of the effective target, including enforced header validation
     * @param policyCoverages observations from reached members
     * @param declarationLocation location of the set keyword, if available
     * @param schemaHit result of the reached schema guard, or null if absent or skipped
     */
    public record PolicySetCoverage(
            @NonNull VoterMetadata voter,
            @NonNull TargetHit targetHit,
            @NonNull List<DocumentCoverage> policyCoverages,
            SourceLocation declarationLocation,
            ConditionHit schemaHit) implements DocumentCoverage {
        public PolicySetCoverage(VoterMetadata voter, TargetHit targetHit, List<DocumentCoverage> policyCoverages) {
            this(voter, targetHit, policyCoverages, null, null);
        }

        public PolicySetCoverage with(DocumentCoverage newCoverage) {
            val aggregatedPolicyCoverage = new ArrayList<>(policyCoverages);
            aggregatedPolicyCoverage.add(newCoverage);
            return new PolicySetCoverage(voter, targetHit, aggregatedPolicyCoverage, declarationLocation, schemaHit);
        }
    }

    /**
     * PDP-level coverage: a top-level voter combining several documents
     * (policies and policy sets). Has no target gate (the PDP applies to
     * every authorization subscription), so unlike
     * {@link PolicySetCoverage} it carries no {@link TargetHit}.
     */
    public record PdpCoverage(@NonNull VoterMetadata voter, @NonNull List<DocumentCoverage> documentCoverages)
            implements DocumentCoverage {
        public PdpCoverage with(DocumentCoverage newCoverage) {
            val aggregated = new ArrayList<>(documentCoverages);
            aggregated.add(newCoverage);
            return new PdpCoverage(voter, aggregated);
        }
    }

    /**
     * @param hits observations of reached conditions
     * @param numberOfConditions number of source conditions, including a schema block
     * @param hasDecisionCondition whether a body condition or enforced schema can affect the vote
     */
    public record BodyCoverage(List<ConditionHit> hits, long numberOfConditions, boolean hasDecisionCondition) {
        public BodyCoverage(List<ConditionHit> hits, long numberOfConditions) {
            this(hits, numberOfConditions, numberOfConditions > hits.stream()
                    .filter(hit -> hit.statementId() < 0 && !hit.enforcedSchema()).count());
        }

        public BodyCoverage with(ConditionHit newHit) {
            val merged = new ArrayList<ConditionHit>(hits.size() + 1);
            merged.addAll(hits);
            merged.add(newHit);
            return new BodyCoverage(merged, numberOfConditions, hasDecisionCondition);
        }
    }

    public sealed interface TargetHit permits BlankTargetHit, NoTargetHit, TargetResult {
    }

    public record NoTargetHit() implements TargetHit {}

    public record BlankTargetHit() implements TargetHit {}

    public record TargetResult(Value match, SourceLocation location) implements TargetHit {}

    /**
     * @param result the observed condition value
     * @param location the condition's source range
     * @param statementId the condition number within the policy, or -1 for a schema block
     * @param enforcedSchema whether the observed block contains an enforced schema
     */
    public record ConditionHit(Value result, SourceLocation location, long statementId, boolean enforcedSchema) {
        public ConditionHit(Value result, SourceLocation location, long statementId) {
            this(result, location, statementId, false);
        }
    }
}
