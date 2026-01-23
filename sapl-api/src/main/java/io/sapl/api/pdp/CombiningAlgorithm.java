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
package io.sapl.api.pdp;

/**
 * Combining algorithm for policy sets, composed of voting mode, default
 * vote, and error handling strategy.
 *
 * @param votingMode how policy decisions are combined
 * @param defaultDecision the vote when no policy applies
 * @param errorHandling how errors are handled during combination
 */
public record CombiningAlgorithm(VotingMode votingMode, DefaultDecision defaultDecision, ErrorHandling errorHandling) {

    /**
     * Default combining algorithm: priority deny, default deny, errors propagate.
     */
    public static final CombiningAlgorithm DEFAULT = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    /**
     * Returns a canonical string representation for hashing and comparison.
     * <p>
     * Format: {@code votingMode:defaultDecision:errorHandling}
     * <br>
     * Example: {@code PRIORITY_DENY:DENY:ABSTAIN}
     * </p>
     * <p>
     * This format is stable and used in configuration ID generation.
     * Changes to this format are breaking changes.
     * </p>
     *
     * @return canonical string representation
     */
    public String toCanonicalString() {
        return votingMode.name() + ":" + defaultDecision.name() + ":" + errorHandling.name();
    }

    public enum VotingMode {
        FIRST,
        PRIORITY_DENY,
        PRIORITY_PERMIT,
        UNANIMOUS,
        UNANIMOUS_STRICT,
        UNIQUE
    }

    public enum DefaultDecision {
        ABSTAIN,
        DENY,
        PERMIT
    }

    public enum ErrorHandling {
        ABSTAIN,
        PROPAGATE
    }
}
