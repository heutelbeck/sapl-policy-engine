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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * The decision artefact a voter produces. Pure data: an authorization
 * decision, the errors that led to INDETERMINATE (if any), the
 * contributing child votes (for combined votes), the voter's identity,
 * and the outcome.
 * <p>
 * Vote does not carry per-evaluation observability metadata. Trace data
 * (which subscriptions were touched, what value each had at evaluation
 * time, when each was retrieved) is the trigger loop's responsibility
 * and lives in a wrapper at the trigger-loop output, not in Vote
 * itself.
 * <p>
 * The {@link #toTrace()} result is computed lazily and cached on first
 * call. Multiple observers (interceptors, reports) reading the trace
 * share one tree. The cache uses the racy single-check idiom: the
 * field is non-{@code volatile}, but {@link ObjectValue} is immutable
 * and its final fields are safely published, so concurrent readers are
 * safe and a benign race only costs a redundant computation.
 */
@Getter
@ToString
@EqualsAndHashCode
@Accessors(fluent = true)
@RequiredArgsConstructor
public final class Vote implements Voter, TracedDecision {

    private final AuthorizationDecision authorizationDecision;
    private final List<ErrorValue>      errors;
    private final List<Vote>            contributingVotes;
    private final VoterMetadata         voter;
    private final Outcome               outcome;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(AccessLevel.NONE)
    private ObjectValue cachedTrace;

    @Override
    public Value trace() {
        return toTrace();
    }

    /**
     * Creates a combined vote from multiple contributing votes.
     */
    public static Vote combinedVote(AuthorizationDecision authorizationDecision, VoterMetadata voter,
            List<Vote> contributingVotes, Outcome outcome) {
        return new Vote(authorizationDecision, List.of(), contributingVotes, voter, outcome);
    }

    /**
     * Creates a vote carrying a decision with obligations, advice, and
     * resource transformation.
     */
    public static Vote of(Decision decision, ArrayValue obligations, ArrayValue advice, Value resource,
            VoterMetadata voter) {
        return new Vote(new AuthorizationDecision(decision, obligations, advice, resource), List.of(), List.of(), voter,
                voter.outcome());
    }

    /**
     * Creates an INDETERMINATE vote carrying the error that produced it.
     */
    public static Vote error(ErrorValue error, VoterMetadata voter) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(error), List.of(), voter, voter.outcome());
    }

    /**
     * Creates a NOT_APPLICABLE vote.
     */
    public static Vote abstain(VoterMetadata voter) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), List.of(), voter, voter.outcome());
    }

    /**
     * Creates a NOT_APPLICABLE vote with the given contributing votes.
     */
    public static Vote abstain(VoterMetadata voter, List<Vote> contributingVotes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), contributingVotes, voter, voter.outcome());
    }

    /**
     * Returns a copy with {@code newVote} appended to the contributing votes.
     */
    public Vote withVote(Vote newVote) {
        val mergedVotes = new ArrayList<>(contributingVotes);
        mergedVotes.add(newVote);
        return new Vote(authorizationDecision, errors, mergedVotes, voter, outcome);
    }

    /**
     * Finalizes a vote by applying default decision and error handling rules.
     */
    public Vote finalizeVote(DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        if (authorizationDecision.decision() == Decision.NOT_APPLICABLE) {
            return switch (defaultDecision) {
            case ABSTAIN -> this;
            case DENY    -> replaceDecision(Decision.DENY, Outcome.DENY);
            case PERMIT  -> replaceDecision(Decision.PERMIT, Outcome.PERMIT);
            };
        }
        if (authorizationDecision.decision() == Decision.INDETERMINATE) {
            return switch (errorHandling) {
            case ABSTAIN   -> replaceDecision(Decision.NOT_APPLICABLE, outcome);
            case PROPAGATE -> this;
            };
        }
        return this;
    }

    /**
     * Replaces the decision while preserving every other field.
     */
    public Vote replaceDecision(Decision decision, Outcome newOutcome) {
        val newAuthorizationDecision = new AuthorizationDecision(decision, authorizationDecision.obligations(),
                authorizationDecision.advice(), authorizationDecision.resource());
        return new Vote(newAuthorizationDecision, errors, contributingVotes, voter, newOutcome);
    }

    /**
     * Converts this vote to a trace ObjectValue. Carries the vote's pure
     * decision data; subscription/attribute trace lives in the trigger-
     * loop wrapper and is not part of {@code Vote.toTrace()}. The result
     * is cached after the first call.
     */
    public ObjectValue toTrace() {
        ObjectValue cache = cachedTrace;
        if (cache == null) {
            cache       = computeTrace();
            cachedTrace = cache;
        }
        return cache;
    }

    private ObjectValue computeTrace() {
        val builder = ObjectValue.builder();
        builder.put("decision", Value.of(authorizationDecision.decision().name()));
        if (!authorizationDecision.obligations().isEmpty()) {
            builder.put("obligations", authorizationDecision.obligations());
        }
        if (!authorizationDecision.advice().isEmpty()) {
            builder.put("advice", authorizationDecision.advice());
        }
        if (!(authorizationDecision.resource() instanceof UndefinedValue)) {
            builder.put("resource", authorizationDecision.resource());
        }
        builder.put("voter", voterToTrace(voter));
        builder.put("outcome", Value.of(outcome.name()));
        if (!errors.isEmpty()) {
            builder.put("errors", ArrayValue.builder().addAll(errors).build());
        }
        if (!contributingVotes.isEmpty()) {
            val votesArray = ArrayValue.builder();
            for (val child : contributingVotes) {
                votesArray.add(child.toTrace());
            }
            builder.put("contributingVotes", votesArray.build());
        }
        return builder.build();
    }

    private static ObjectValue voterToTrace(VoterMetadata voterMetadata) {
        val builder = ObjectValue.builder().put("name", Value.of(voterMetadata.name()))
                .put("pdpId", Value.of(voterMetadata.pdpId()))
                .put("configurationId", Value.of(voterMetadata.configurationId()))
                .put("outcome", Value.of(voterMetadata.outcome().name()));
        switch (voterMetadata) {
        case PolicyVoterMetadata(var nameIgnored, var pdpIdIgnored, var configIdIgnored, var docId, var outcomeIgnored, var hasConstraintsIgnored) when docId != null ->
            builder.put("documentId", Value.of(docId));
        case PolicySetVoterMetadata(var nameIgnored, var pdpIdIgnored, var configIdIgnored, var docId, var algo, var outcomeIgnored, var hasConstraintsIgnored)       -> {
            if (docId != null) {
                builder.put("documentId", Value.of(docId));
            }
            if (algo != null) {
                builder.put("algorithm", algorithmToTrace(algo));
            }
        }
        case PdpVoterMetadata(var nameIgnored, var pdpIdIgnored, var configIdIgnored, var algo, var outcomeIgnored, var hasConstraintsIgnored) when algo != null      ->
            builder.put("algorithm", algorithmToTrace(algo));
        default                                                                                                                                                       ->
            { /* NO-OP */}
        }
        return builder.build();
    }

    private static ObjectValue algorithmToTrace(CombiningAlgorithm algorithm) {
        return ObjectValue.builder().put("votingMode", Value.of(algorithm.votingMode().name()))
                .put("defaultDecision", Value.of(algorithm.defaultDecision().name()))
                .put("errorHandling", Value.of(algorithm.errorHandling().name())).build();
    }
}
