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
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record Vote(
        AuthorizationDecision authorizationDecision,
        List<ErrorValue> errors,
        List<AttributeRecord> contributingAttributes,
        List<Vote> contributingVotes,
        VoterMetadata voter,
        Outcome outcome) implements Voter {

    /**
     * Creates a combined vote from multiple contributing votes.
     *
     * @param authorizationDecision the authorization decision
     * @param voter the voter metadata
     * @param contributingVotes the votes that contributed to this decision
     * @param outcome the extended indeterminate outcome
     * @return a new combined vote
     */
    public static Vote combinedVote(AuthorizationDecision authorizationDecision, VoterMetadata voter,
            List<Vote> contributingVotes, Outcome outcome) {
        return new Vote(authorizationDecision, List.of(), List.of(), contributingVotes, voter, outcome);
    }

    /**
     * Creates a vote with traced attributes.
     *
     * @param decision the decision
     * @param obligations the obligations
     * @param advice the advice
     * @param resource the resource transformation
     * @param voter the voter metadata
     * @param contributingAttributes the attributes accessed during evaluation
     * @return a new traced vote
     */
    public static Vote tracedVote(Decision decision, ArrayValue obligations, ArrayValue advice, Value resource,
            VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(new AuthorizationDecision(decision, obligations, advice, resource), List.of(),
                contributingAttributes, List.of(), voter, voter.outcome());
    }

    /**
     * Creates an error vote with INDETERMINATE decision.
     *
     * @param error the error that occurred
     * @param voter the voter metadata
     * @return a new error vote
     */
    public static Vote error(ErrorValue error, VoterMetadata voter) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(error), List.of(), List.of(), voter,
                voter.outcome());
    }

    /**
     * Creates an error vote with traced attributes.
     *
     * @param error the error that occurred
     * @param voter the voter metadata
     * @param contributingAttributes the attributes accessed before the error
     * @return a new traced error vote
     */
    public static Vote tracedError(ErrorValue error, VoterMetadata voter,
            List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(error), contributingAttributes, List.of(), voter,
                voter.outcome());
    }

    /**
     * Creates an abstain vote with NOT_APPLICABLE decision.
     *
     * @param voter the voter metadata
     * @return a new abstain vote
     */
    public static Vote abstain(VoterMetadata voter) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), List.of(), List.of(), voter, voter.outcome());
    }

    /**
     * Creates an abstain vote with traced attributes.
     *
     * @param voter the voter metadata
     * @param contributingAttributes the attributes accessed during evaluation
     * @return a new traced abstain vote
     */
    public static Vote tracedAbstain(VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), contributingAttributes, List.of(), voter,
                voter.outcome());
    }

    /**
     * Creates an abstain vote with contributing votes.
     *
     * @param voter the voter metadata
     * @param contributingVotes the votes that contributed to this abstain
     * @return a new abstain vote with contributing votes
     */
    public static Vote abstain(VoterMetadata voter, List<Vote> contributingVotes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), List.of(), contributingVotes, voter,
                voter.outcome());
    }

    /**
     * Creates a new vote with an additional contributing vote.
     *
     * @param newVote the vote to add
     * @return a new vote with the additional contributing vote
     */
    public Vote withVote(Vote newVote) {
        val mergedVotes = new ArrayList<>(contributingVotes);
        mergedVotes.add(newVote);
        return new Vote(authorizationDecision, errors, contributingAttributes, mergedVotes, voter, outcome);
    }

    /**
     * Recursively aggregates all contributing attributes from this vote and all
     * nested contributing votes in the tree.
     *
     * @return all attributes from this vote and its entire contributing vote tree
     */
    public List<AttributeRecord> aggregatedContributingAttributes() {
        val all = new ArrayList<>(contributingAttributes);
        for (val childVote : contributingVotes) {
            all.addAll(childVote.aggregatedContributingAttributes());
        }
        return all;
    }

    /**
     * Finalizes a vote by applying default decision and error handling rules.
     * <p>
     * If the vote is NOT_APPLICABLE, applies the default decision.
     * If the vote is INDETERMINATE, applies the error handling rule.
     *
     * @param defaultDecision the decision when NOT_APPLICABLE
     * @param errorHandling how to handle INDETERMINATE
     * @return the finalized vote
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
     * Replaces the decision in this vote while preserving other fields.
     *
     * @param decision the new decision
     * @param newOutcome the new outcome
     * @return a new vote with the replaced decision
     */
    public Vote replaceDecision(Decision decision, Outcome newOutcome) {
        val newAuthorizationDecision = new AuthorizationDecision(decision, authorizationDecision.obligations(),
                authorizationDecision.advice(), authorizationDecision.resource());
        return new Vote(newAuthorizationDecision, errors, contributingAttributes, contributingVotes, voter, newOutcome);
    }

    /**
     * Converts this vote to a trace ObjectValue.
     *
     * @return an ObjectValue containing the vote structure
     */
    public ObjectValue toTrace() {
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
        if (!contributingAttributes.isEmpty()) {
            val attrArray = ArrayValue.builder();
            for (val attr : contributingAttributes) {
                attrArray.add(attributeToTrace(attr));
            }
            builder.put("attributes", attrArray.build());
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

    private static ObjectValue attributeToTrace(AttributeRecord attr) {
        val invocation = attr.invocation();
        val builder    = ObjectValue.builder().put("attributeName", Value.of(invocation.attributeName()))
                .put("configurationId", Value.of(invocation.configurationId())).put("value", attr.attributeValue())
                .put("retrievedAt", Value.of(attr.retrievedAt().toString()));
        if (invocation.entity() != null) {
            builder.put("entity", invocation.entity());
        }
        if (!invocation.arguments().isEmpty()) {
            builder.put("arguments", ArrayValue.builder().addAll(invocation.arguments()).build());
        }
        return builder.build();
    }
}
