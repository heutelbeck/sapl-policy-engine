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
package io.sapl.ast;

/**
 * Combining algorithm for policy sets, composed of voting mode, default
 * vote, and errors handling strategy.
 *
 * @param votingMode how policy decisions are combined
 * @param defaultDecision the vote when no policy applies
 * @param errorHandling how errors are handled during combination
 */
public record CombiningAlgorithm(VotingMode votingMode, DefaultDecision defaultDecision, ErrorHandling errorHandling) {

    public enum VotingMode {
        DENY_WINS,
        FIRST_VOTE,
        PERMIT_WINS,
        UNANIMOUS_DECISION,
        UNIQUE_DECISION
    }

    public enum DefaultDecision {
        DENY,
        ABSTAIN,
        PERMIT
    }

    public enum ErrorHandling {
        ABSTAIN,
        PROPAGATE
    }
}
