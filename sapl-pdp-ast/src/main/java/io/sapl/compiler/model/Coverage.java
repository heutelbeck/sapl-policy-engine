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
import io.sapl.compiler.pdp.VoterMetadata;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record Coverage(List<DocumentCoverage> coverage) {

    public static final TargetHit NO_TARGET_HIT = new Coverage.NoTargetHit();
    public static final TargetHit BLANK_TARGET_HIT = new Coverage.BlankTargetHit();

    public sealed interface DocumentCoverage permits PolicyCoverage, PolicySetCoverage {
        VoterMetadata voter();
    }

    public record PolicyCoverage(VoterMetadata voter, BodyCoverage bodyCoverage) implements DocumentCoverage {}

    public record PolicySetCoverage(
            @NonNull VoterMetadata voter,
            @NonNull TargetHit targetHit,
            @NonNull List<DocumentCoverage> policyCoverages) implements DocumentCoverage {
        public PolicySetCoverage with(DocumentCoverage newCoverage) {
            val aggregatedPolicyCoverage = new ArrayList<>(policyCoverages);
            aggregatedPolicyCoverage.add(newCoverage);
            return new PolicySetCoverage(voter, targetHit, aggregatedPolicyCoverage);
        }
    }

    public record BodyCoverage(List<ConditionHit> hits, long numberOfConditions) {
        public BodyCoverage with(ConditionHit newHit) {
            val merged = new ArrayList<ConditionHit>(hits.size() + 1);
            merged.addAll(hits);
            merged.add(newHit);
            return new BodyCoverage(merged, numberOfConditions);
        }
    }

    public sealed interface TargetHit permits BlankTargetHit, NoTargetHit, TargetResult {
    }

    public record NoTargetHit() implements TargetHit {}

    public record BlankTargetHit() implements TargetHit {}

    public record TargetResult(Value match, SourceLocation location) implements TargetHit {}

    public record ConditionHit(Value result, SourceLocation location, long statementId) {}
}
