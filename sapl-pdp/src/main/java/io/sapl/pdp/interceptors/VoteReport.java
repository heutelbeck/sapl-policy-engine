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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.compiler.document.Vote;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * A concise report extracted from a Vote for logging and auditing purposes.
 * <p>
 * Unlike {@link Vote#toTrace()} which produces a full hierarchical trace,
 * this record provides a flattened summary view with essential information.
 *
 * @param decision the final authorization decision
 * @param obligations obligations from the decision
 * @param advice advice from the decision
 * @param resource resource transformation (if any)
 * @param voterName name of the top-level voter
 * @param pdpId the PDP identifier
 * @param configurationId the configuration identifier
 * @param algorithm combining algorithm (for policy sets)
 * @param contributingDocuments all documents that contributed to the decision
 * (flattened), each with its own attributes
 * @param errors errors encountered during evaluation
 */
public record VoteReport(
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
     * Extracts a concise report from a Vote.
     *
     * @param vote the vote to extract from
     * @return a VoteReport containing the essential information
     */
    public static VoteReport from(Vote vote) {
        val authz = vote.authorizationDecision();
        val voter = vote.voter();

        var algorithm = (CombiningAlgorithm) null;
        if (voter instanceof PolicySetVoterMetadata psm) {
            algorithm = psm.combiningAlgorithm();
        }

        return new VoteReport(authz.decision(), authz.obligations(), authz.advice(), authz.resource(), voter.name(),
                voter.pdpId(), voter.configurationId(), algorithm, collectContributingDocuments(vote), vote.errors());
    }

    private static List<ContributingDocument> collectContributingDocuments(Vote vote) {
        val documents = new ArrayList<ContributingDocument>();
        collectDocumentsRecursively(vote.contributingVotes(), documents);
        return documents;
    }

    private static void collectDocumentsRecursively(List<Vote> votes, List<ContributingDocument> accumulator) {
        for (val v : votes) {
            accumulator.add(new ContributingDocument(v.voter().name(), v.authorizationDecision().decision(),
                    v.contributingAttributes(), v.errors()));
            collectDocumentsRecursively(v.contributingVotes(), accumulator);
        }
    }
}
