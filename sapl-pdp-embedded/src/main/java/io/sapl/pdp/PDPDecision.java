/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PDPDecision implements TracedDecision {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new Jdk8Module());
    }

    AuthorizationSubscription authorizationSubscription;
    PolicyRetrievalResult     prpResult;
    CombinedDecision          combinedDecision;
    Instant                   timestamp;
    LinkedList<Modification>  modifications = new LinkedList<>();
    BaseJsonNode              metadata      = null;

    private record Modification(AuthorizationDecision authorizationDecision, String explanation) {}

    private PDPDecision(AuthorizationSubscription authorizationSubscription,
            PolicyRetrievalResult prpResult,
            CombinedDecision combinedDecision,
            Instant timestamp,
            List<Modification> modifications,
            BaseJsonNode metadata) {
        this.authorizationSubscription = authorizationSubscription;
        this.combinedDecision          = combinedDecision;
        this.timestamp                 = timestamp;
        this.prpResult                 = prpResult;
        this.metadata                  = metadata;
        this.modifications.addAll(modifications);
    }

    public static PDPDecision of(AuthorizationSubscription authorizationSubscription, CombinedDecision combinedDecision,
            PolicyRetrievalResult prpResult) {
        return new PDPDecision(authorizationSubscription, prpResult, combinedDecision, Instant.now(), List.of(), null);
    }

    public static PDPDecision of(AuthorizationSubscription authorizationSubscription,
            CombinedDecision combinedDecision) {
        return new PDPDecision(authorizationSubscription, null, combinedDecision, Instant.now(), List.of(), null);
    }

    public static PDPDecision of(AuthorizationSubscription authorizationSubscription, CombinedDecision combinedDecision,
            PolicyRetrievalResult prpResult, BaseJsonNode metadata) {
        return new PDPDecision(authorizationSubscription, prpResult, combinedDecision, Instant.now(), List.of(),
                metadata);
    }

    public static PDPDecision of(AuthorizationSubscription authorizationSubscription, CombinedDecision combinedDecision,
            BaseJsonNode metadata) {
        return new PDPDecision(authorizationSubscription, null, combinedDecision, Instant.now(), List.of(), metadata);
    }

    @Override
    public AuthorizationDecision getAuthorizationDecision() {
        if (modifications.isEmpty())
            return combinedDecision.getAuthorizationDecision();
        return modifications.peekLast().authorizationDecision();
    }

    @Override
    public TracedDecision modified(AuthorizationDecision authzDecision, String explanation) {
        final var modified = new PDPDecision(authorizationSubscription, prpResult, combinedDecision, timestamp,
                modifications, metadata);
        modified.modifications.add(new Modification(authzDecision, explanation));
        return modified;
    }

    @Override
    public JsonNode getTrace() {
        final var trace = Val.JSON.objectNode();
        trace.set(Trace.OPERATOR, Val.JSON.textNode("Policy Decision Point"));
        trace.set(Trace.AUTHORIZATION_SUBSCRIPTION, MAPPER.valueToTree(authorizationSubscription));
        trace.set(Trace.AUTHORIZATION_DECISION, MAPPER.valueToTree(getAuthorizationDecision()));
        final var matches = Val.JSON.arrayNode();
        prpResult.getMatchingDocuments().forEach(doc -> matches.add(matchTrace(doc)));
        trace.set(Trace.MATCHING_DOCUMENTS, matches);
        final var nonMatches = Val.JSON.arrayNode();
        prpResult.getNonMatchingDocuments().forEach(doc -> nonMatches.add(matchTrace(doc)));
        trace.set(Trace.NON_MATCHING_DOCUMENTS, nonMatches);
        trace.set(Trace.PRP_INCONSISTENT, Val.JSON.booleanNode(prpResult.isPrpInconsistent()));
        trace.set(Trace.RETRIEVAL_HAS_ERRORS, Val.JSON.booleanNode(prpResult.isRetrievalWithErrors()));
        if (prpResult.getErrorMessage() != null) {
            trace.set(Trace.RETRIEVAL_ERROR_MESSAGE, Val.JSON.textNode(prpResult.getErrorMessage()));
        }
        trace.set(Trace.COMBINED_DECISION, combinedDecision.getTrace());
        trace.set(Trace.TIMESTAMP, Val.JSON.textNode(timestamp.toString()));
        if (!modifications.isEmpty()) {
            trace.set(Trace.MODIFICATIONS, getModificationsTrace());
        }
        trace.set(Trace.METADATA, this.metadata);
        return trace;
    }

    private JsonNode matchTrace(DocumentMatch matchingDocument) {
        final var trace = Val.JSON.objectNode();
        trace.set(Trace.DOCUMENT_IDENTIFIER, Val.JSON.textNode(matchingDocument.document().id()));
        trace.set(Trace.DOCUMENT_NAME,
                Val.JSON.textNode(matchingDocument.document().sapl().getPolicyElement().getSaplName()));
        trace.set(Trace.TARGET, matchingDocument.targetExpressionResult().getTrace());
        return trace;
    }

    private JsonNode getModificationsTrace() {
        final var modificationTrace = Val.JSON.arrayNode();
        for (var mod : modifications) {
            final var modJson = Val.JSON.objectNode();
            modJson.set(Trace.AUTHORIZATION_DECISION, MAPPER.valueToTree(mod.authorizationDecision()));
            modJson.set(Trace.EXPLANATION, Val.JSON.textNode(mod.explanation()));
            modificationTrace.add(modJson);
        }
        return modificationTrace;
    }

    @Override
    public Collection<Val> getErrorsFromTrace() {
        final var all = new ArrayList<Val>();
        all.addAll(combinedDecision.getErrorsFromTrace());
        all.addAll(prpResult.getErrors());
        return all;
    }
}
