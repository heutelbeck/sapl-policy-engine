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

    // Convenience constants for common combining algorithms (backwards
    // compatibility)

    /**
     * Deny overrides: if any policy denies, the result is deny.
     * Equivalent to: priority deny or deny errors propagate
     */
    public static final CombiningAlgorithm DENY_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    /**
     * Permit overrides: if any policy permits, the result is permit.
     * Equivalent to: priority permit or permit errors propagate
     */
    public static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);

    /**
     * Only one applicable: exactly one policy must match, otherwise indeterminate.
     * Equivalent to: unique or abstain errors propagate
     */
    public static final CombiningAlgorithm ONLY_ONE_APPLICABLE = new CombiningAlgorithm(VotingMode.UNIQUE,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    /**
     * Deny unless permit: default is deny, first matching policy wins.
     * Equivalent to: first or deny errors abstain
     */
    public static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(VotingMode.FIRST,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    /**
     * Permit unless deny: default is permit, first matching policy wins.
     * Equivalent to: first or permit errors abstain
     */
    public static final CombiningAlgorithm PERMIT_UNLESS_DENY = new CombiningAlgorithm(VotingMode.FIRST,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);

    /**
     * First applicable: first matching policy wins, abstain if none match.
     * Equivalent to: first or abstain errors propagate
     */
    public static final CombiningAlgorithm FIRST_APPLICABLE = new CombiningAlgorithm(VotingMode.FIRST,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

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
