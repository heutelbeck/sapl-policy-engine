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

    // Field name constants
    private static final String FIELD_ADVICE = "advice";
    private static final String FIELD_ALGORITHM = "algorithm";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_ATTRIBUTE_NAME = "attributeName";
    private static final String FIELD_ATTRIBUTES = "attributes";
    private static final String FIELD_CONFIGURATION_ID = "configurationId";
    private static final String FIELD_CONTRIBUTING_VOTES = "contributingVotes";
    private static final String FIELD_DECISION = "decision";
    private static final String FIELD_DEFAULT_DECISION = "defaultDecision";
    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_END = "end";
    private static final String FIELD_ENTITY = "entity";
    private static final String FIELD_ERROR_HANDLING = "errorHandling";
    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_LINE = "line";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_OBLIGATIONS = "obligations";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_PDP_ID = "pdpId";
    private static final String FIELD_RESOURCE = "resource";
    private static final String FIELD_RETRIEVED_AT = "retrievedAt";
    private static final String FIELD_START = "start";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_VOTER = "voter";
    private static final String FIELD_VOTING_MODE = "votingMode";

    // Type value constants
    private static final String TYPE_POLICY = "policy";
    private static final String TYPE_SET = "set";
    private static final String TYPE_UNKNOWN = "unknown";

    public static Vote combinedVote(AuthorizationDecision authorizationDecision, VoterMetadata voter,
            List<Vote> contributingVotes, Outcome outcome) {
        return new Vote(authorizationDecision, List.of(), List.of(), contributingVotes, voter, outcome);
    }

    public static Vote tracedVote(Decision decision, ArrayValue obligations, ArrayValue advice, Value resource,
            VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(new AuthorizationDecision(decision, obligations, advice, resource), List.of(),
                contributingAttributes, List.of(), voter, voter.outcome());
    }

    public static Vote error(ErrorValue error, VoterMetadata voter) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(error), List.of(), List.of(), voter,
                voter.outcome());
    }

    public static Vote tracedError(ErrorValue error, VoterMetadata voter,
            List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.INDETERMINATE, List.of(error), contributingAttributes, List.of(), voter,
                voter.outcome());
    }

    public static Vote abstain(VoterMetadata voter) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), List.of(), List.of(), voter, voter.outcome());
    }

    public static Vote tracedAbstain(VoterMetadata voter, List<AttributeRecord> contributingAttributes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), contributingAttributes, List.of(), voter,
                voter.outcome());
    }

    public static Vote abstain(VoterMetadata voter, List<Vote> contributingVotes) {
        return new Vote(AuthorizationDecision.NOT_APPLICABLE, List.of(), List.of(), contributingVotes, voter,
                voter.outcome());
    }

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
     * Converts this vote to a detailed trace ObjectValue for debugging and
     * auditing.
     * <p>
     * The trace includes the complete recursive structure of the decision tree,
     * showing all contributing votes, errors, and attributes.
     *
     * @return an ObjectValue containing the full hierarchical trace
     */
    public ObjectValue toTrace() {
        val builder = ObjectValue.builder();

        builder.put(FIELD_DECISION, Value.of(authorizationDecision.decision().name()));
        if (!authorizationDecision.obligations().isEmpty()) {
            builder.put(FIELD_OBLIGATIONS, authorizationDecision.obligations());
        }
        if (!authorizationDecision.advice().isEmpty()) {
            builder.put(FIELD_ADVICE, authorizationDecision.advice());
        }
        if (!(authorizationDecision.resource() instanceof UndefinedValue)) {
            builder.put(FIELD_RESOURCE, authorizationDecision.resource());
        }

        builder.put(FIELD_VOTER, voterToTrace(voter));
        builder.put(FIELD_OUTCOME, Value.of(outcome.name()));

        if (!errors.isEmpty()) {
            val errArray = ArrayValue.builder();
            for (val err : errors) {
                errArray.add(errorToTrace(err));
            }
            builder.put(FIELD_ERRORS, errArray.build());
        }

        if (!contributingAttributes.isEmpty()) {
            val attrArray = ArrayValue.builder();
            for (val attr : contributingAttributes) {
                attrArray.add(attributeToTrace(attr));
            }
            builder.put(FIELD_ATTRIBUTES, attrArray.build());
        }

        if (!contributingVotes.isEmpty()) {
            val votesArray = ArrayValue.builder();
            for (val child : contributingVotes) {
                votesArray.add(child.toTrace());
            }
            builder.put(FIELD_CONTRIBUTING_VOTES, votesArray.build());
        }

        return builder.build();
    }

    private static ObjectValue voterToTrace(VoterMetadata voterMetadata) {
        val builder = ObjectValue.builder().put(FIELD_NAME, Value.of(voterMetadata.name()))
                .put(FIELD_PDP_ID, Value.of(voterMetadata.pdpId()))
                .put(FIELD_CONFIGURATION_ID, Value.of(voterMetadata.configurationId()))
                .put(FIELD_OUTCOME, Value.of(voterMetadata.outcome().name()));

        switch (voterMetadata) {
        case PolicyVoterMetadata p     -> {
            builder.put(FIELD_TYPE, Value.of(TYPE_POLICY));
            if (p.documentId() != null) {
                builder.put(FIELD_DOCUMENT_ID, Value.of(p.documentId()));
            }
        }
        case PolicySetVoterMetadata ps -> {
            builder.put(FIELD_TYPE, Value.of(TYPE_SET));
            if (ps.documentId() != null) {
                builder.put(FIELD_DOCUMENT_ID, Value.of(ps.documentId()));
            }
            if (ps.combiningAlgorithm() != null) {
                builder.put(FIELD_ALGORITHM, algorithmToTrace(ps.combiningAlgorithm()));
            }
        }
        default                        -> builder.put(FIELD_TYPE, Value.of(TYPE_UNKNOWN));
        }

        return builder.build();
    }

    private static ObjectValue algorithmToTrace(CombiningAlgorithm algorithm) {
        return ObjectValue.builder().put(FIELD_VOTING_MODE, Value.of(algorithm.votingMode().name()))
                .put(FIELD_DEFAULT_DECISION, Value.of(algorithm.defaultDecision().name()))
                .put(FIELD_ERROR_HANDLING, Value.of(algorithm.errorHandling().name())).build();
    }

    private static ObjectValue errorToTrace(ErrorValue error) {
        val builder = ObjectValue.builder().put(FIELD_MESSAGE, Value.of(error.message()));
        if (error.location() != null) {
            builder.put(FIELD_LINE, Value.of(error.location().line()))
                    .put(FIELD_START, Value.of(error.location().start()))
                    .put(FIELD_END, Value.of(error.location().end()));
        }
        return builder.build();
    }

    private static ObjectValue attributeToTrace(AttributeRecord attr) {
        val builder = ObjectValue.builder().put(FIELD_ATTRIBUTE_NAME, Value.of(attr.invocation().attributeName()))
                .put(FIELD_CONFIGURATION_ID, Value.of(attr.invocation().configurationId()))
                .put(FIELD_VALUE, attr.attributeValue())
                .put(FIELD_RETRIEVED_AT, Value.of(attr.retrievedAt().toString()));

        if (attr.invocation().entity() != null) {
            builder.put(FIELD_ENTITY, attr.invocation().entity());
        }
        if (!attr.invocation().arguments().isEmpty()) {
            builder.put(FIELD_ARGUMENTS, ArrayValue.builder().addAll(attr.invocation().arguments()).build());
        }
        if (attr.location() != null) {
            builder.put(FIELD_LINE, Value.of(attr.location().line()))
                    .put(FIELD_START, Value.of(attr.location().start()))
                    .put(FIELD_END, Value.of(attr.location().end()));
        }

        return builder.build();
    }
}
