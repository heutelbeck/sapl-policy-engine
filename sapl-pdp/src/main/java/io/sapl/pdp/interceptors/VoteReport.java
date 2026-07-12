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
package io.sapl.pdp.interceptors;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.compiler.document.AttributeContribution;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import lombok.val;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A flattened summary of a decision for logging and auditing. Each
 * contributing document appears as its own entry with the attribute
 * reads referenced from that document's source.
 *
 * @param timestamp the emit timestamp of the decision
 * @param subscriptionId the subscription identifier
 * @param authorizationSubscription the authorization subscription
 * that was evaluated
 * @param decision the final authorization decision
 * @param obligations obligations from the decision
 * @param advice advice from the decision
 * @param resource resource transformation, if any
 * @param voterName name of the top-level voter
 * @param pdpId the PDP identifier
 * @param configurationId the configuration identifier
 * @param algorithm combining algorithm, for policy sets
 * @param contributingDocuments the contributing documents in evaluation
 * order, flattened, each carrying its own attribute reads
 * @param errors errors encountered during evaluation
 */
public record VoteReport(
        Instant timestamp,
        String subscriptionId,
        AuthorizationSubscription authorizationSubscription,
        Decision decision,
        ArrayValue obligations,
        ArrayValue advice,
        Value resource,
        String voterName,
        String pdpId,
        String configurationId,
        CombiningAlgorithm algorithm,
        List<ContributingDocument> contributingDocuments,
        List<ErrorValue> errors) {

    /**
     * Extracts a concise report from a {@link TracedVote}.
     */
    public static VoteReport from(TracedVote tracedVote, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        val vote  = tracedVote.vote();
        val authz = vote.authorizationDecision();
        val voter = vote.voter();

        CombiningAlgorithm algorithm = null;
        if (voter instanceof PolicySetVoterMetadata psm) {
            algorithm = psm.combiningAlgorithm();
        }

        val documents = collectContributingDocuments(vote, tracedVote.dependencies(), tracedVote.readSnapshot());
        return new VoteReport(tracedVote.timestamp(), subscriptionId, authorizationSubscription, authz.decision(),
                authz.obligations(), authz.advice(), authz.resource(), voter.name(), voter.pdpId(),
                voter.configurationId(), algorithm, documents, vote.errors());
    }

    private static List<ContributingDocument> collectContributingDocuments(Vote vote,
            Map<SubscriptionKey, List<Occurrence>> dependencies, Map<SubscriptionKey, AttributeSnapshot> readSnapshot) {
        val documents   = new ArrayList<ContributingDocument>();
        val rootSetName = vote.voter() instanceof PolicySetVoterMetadata ? vote.voter().name() : null;
        collectDocumentsRecursively(vote.contributingVotes(), rootSetName, dependencies, readSnapshot, documents);
        return documents;
    }

    private static void collectDocumentsRecursively(List<Vote> votes, String enclosingSetName,
            Map<SubscriptionKey, List<Occurrence>> dependencies, Map<SubscriptionKey, AttributeSnapshot> readSnapshot,
            List<ContributingDocument> accumulator) {
        for (val v : votes) {
            val isPolicySet = v.voter() instanceof PolicySetVoterMetadata;
            val name        = enclosingSetName == null || isPolicySet ? v.voter().name()
                    : enclosingSetName + "->" + v.voter().name();
            accumulator.add(new ContributingDocument(name, v.authorizationDecision().decision(), v.errors(),
                    attributesFor(name, dependencies, readSnapshot)));
            val childSetName = isPolicySet ? v.voter().name() : enclosingSetName;
            collectDocumentsRecursively(v.contributingVotes(), childSetName, dependencies, readSnapshot, accumulator);
        }
    }

    private static List<AttributeContribution> attributesFor(String documentName,
            Map<SubscriptionKey, List<Occurrence>> dependencies, Map<SubscriptionKey, AttributeSnapshot> readSnapshot) {
        val result = new ArrayList<AttributeContribution>();
        for (val entry : dependencies.entrySet()) {
            val occurrencesInDocument = entry.getValue().stream()
                    .filter(occ -> documentName.equals(occ.location().documentName())).toList();
            val snapshot              = readSnapshot.get(entry.getKey());
            if (occurrencesInDocument.isEmpty() || snapshot == null) {
                continue;
            }
            result.add(new AttributeContribution(entry.getKey(), snapshot.value(), snapshot.timestamp(),
                    occurrencesInDocument));
        }
        return List.copyOf(result);
    }
}
