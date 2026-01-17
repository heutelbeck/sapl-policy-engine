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
package io.sapl.compiler.pdp;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Vote(
        AuthorizationDecision authorizationDecision,
        Optional<ErrorValue> error,
        List<AttributeRecord> contributingAttributes,
        List<Vote> contributingVotes,
        VoterMetadata voter) implements Voter {

    public static Vote combinedVote(AuthorizationDecision authorizationDecision, VoterMetadata voter,
            List<Vote> contributingVotes) {
        return new Vote(authorizationDecision, Optional.empty(), List.of(), contributingVotes, voter);
    }

    public static Vote tracedVote(Decision decision, ArrayValue obligations, ArrayValue advice, Value resource,
            VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(new AuthorizationDecision(decision, obligations, advice, resource), Optional.empty(),
                contributingAttributes, List.of(), voter);
    }

    public static Vote error(ErrorValue error, VoterMetadata voter) {
        return new Vote(AuthorizationDecision.INDETERMINATE, Optional.of(error), List.of(), List.of(), voter);
    }

    public static Vote tracedError(ErrorValue error, VoterMetadata voter,
            List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.INDETERMINATE, Optional.of(error), contributingAttributes, List.of(),
                voter);
    }

    public static Vote abstain(VoterMetadata voter) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, Optional.empty(), List.of(), List.of(), voter);
    }

    public static Vote tracedAbstain(VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, Optional.empty(), contributingAttributes, List.of(),
                voter);
    }

    public static Vote abstain(VoterMetadata voter, List<Vote> contributingVotes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, Optional.empty(), List.of(), contributingVotes, voter);
    }

    public Vote withVote(Vote newVote) {
        val mergedVotes = new ArrayList<>(contributingVotes);
        mergedVotes.add(newVote);
        return new Vote(authorizationDecision, error, contributingAttributes, mergedVotes, voter);
    }

    public Vote withAttributes(List<AttributeRecord> newAttributes) {
        val mergedAttributes = new ArrayList<>(contributingAttributes);
        mergedAttributes.addAll(newAttributes);
        return new Vote(authorizationDecision, error, mergedAttributes, contributingVotes, voter);
    }
}
