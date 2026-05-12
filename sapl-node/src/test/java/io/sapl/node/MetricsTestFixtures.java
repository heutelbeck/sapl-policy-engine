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
package io.sapl.node;

import java.time.Instant;
import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Shared fixtures for tests exercising the PDP metrics path.
 */
@UtilityClass
class MetricsTestFixtures {

    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-02-13T00:00:00Z");

    static TracedVote voteWithDecision(Decision decision) {
        val authzDecision = switch (decision) {
                          case PERMIT         -> AuthorizationDecision.PERMIT;
                          case DENY           -> AuthorizationDecision.DENY;
                          case SUSPEND        -> AuthorizationDecision.SUSPEND;
                          case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
                          case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
                          };
        val voterMetadata = new PdpVoterMetadata("pdp", "default", "config-1", null, Outcome.PERMIT, false);
        val vote          = new Vote(authzDecision, List.of(), List.of(), voterMetadata, voterMetadata.outcome());
        return TracedVote.of(vote, FIXED_TIMESTAMP);
    }
}
