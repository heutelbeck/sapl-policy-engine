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
package io.sapl.compiler.document;

import io.sapl.api.model.CompiledExpression;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.policy.CoverageVoter;
import reactor.core.publisher.Flux;

public interface CompiledDocument {

    VoterMetadata metadata();

    default Outcome outcome() {
        return metadata().outcome();
    }

    CompiledExpression isApplicable();

    Voter voter();

    Voter applicabilityAndVote();

    CoverageVoter coverageVoter();

    /**
     * Reactor-based coverage stream. Records that have not migrated to
     * the snapshot path still expose a {@link Flux} field that overrides
     * this default. Records that have migrated drop the field and
     * inherit the throw, so any caller still reaching for the Reactor
     * pipeline fails loudly at runtime rather than silently producing
     * degraded results.
     */
    default Flux<VoteWithCoverage> coverage() {
        throw new UnsupportedOperationException(
                "Reactor coverage() removed; use coverageVoter().evaluate(ctx) on " + getClass().getSimpleName());
    }
}
