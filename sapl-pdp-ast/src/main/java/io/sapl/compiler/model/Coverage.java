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

import java.util.List;

public record Coverage(List<DocumentCoverage> coverage) {
    public sealed interface DocumentCoverage permits PolicyCoverage, PolicySetCoverage {
        String documentName();

        TargetHit targetHit();

        String documentSource();

        String documentId(); // e.g., filename or DB Id
    }

    public record PolicyCoverage(
            String documentName,
            TargetHit targetHit,
            String documentSource,
            String documentId,
            BodyCoverage bodyCoverage) implements DocumentCoverage {}

    public record PolicySetCoverage(
            String documentName,
            TargetHit targetHit,
            String documentSource,
            String documentId,
            List<PolicyCoverage> policyCoverages) implements DocumentCoverage {}

    public sealed interface TargetHit permits ConstantTarget, ConditionHit {
    }

    public record ConstantTarget() implements TargetHit {}

    public record BodyCoverage(List<ConditionHit> hits, long numberOfConditions) {}

    public record ConditionHit(long statementId, Value result, SourceLocation location) implements TargetHit {}
}
