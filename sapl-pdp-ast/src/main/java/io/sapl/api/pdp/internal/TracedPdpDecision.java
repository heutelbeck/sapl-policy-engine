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
package io.sapl.api.pdp.internal;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder and utilities for Level 3 traced decisions: PDP-level combining
 * results.
 * <p>
 * A TracedPdpDecision captures the complete result of evaluating an
 * authorization subscription at the PDP level,
 * including PDP metadata, the combined authorization decision, aggregate counts
 * for completeness proof, and traces of
 * all evaluated documents.
 * <p>
 * Structure:
 *
 * <pre>
 * {
 *   "decision": "PERMIT",
 *   "obligations": [...],
 *   "advice": [...],
 *   "resource": ...,
 *   "trace": {
 *     "pdpId": "...",
 *     "configurationId": "...",
 *     "subscriptionId": "...",
 *     "subscription": { "subject": ..., "action": ..., "resource": ..., "environment": ... },
 *     "timestamp": "...",
 *     "algorithm": "deny-overrides",
 *     "totalDocuments": 50,
 *     "documents": [
 *       { TracedPolicyDecision or TracedPolicySetDecision }
 *     ]
 *   }
 * }
 * </pre>
 */
@UtilityClass
public class TracedPdpDecision {

    private static final String SUBJECT     = "subject";
    private static final String ACTION      = "action";
    private static final String RESOURCE_EL = "resource";
    private static final String ENVIRONMENT = "environment";

    /**
     * Creates a new builder for constructing a TracedPdpDecision Value.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for fluent TracedPdpDecision construction.
     */
    public static final class Builder {
        private String                    pdpId;
        private String                    configurationId;
        private String                    subscriptionId;
        private AuthorizationSubscription subscription;
        private String                    timestamp;
        private String                    algorithm;
        private Decision                  decision;
        private int                       totalDocuments;
        private ArrayValue                obligations     = Value.EMPTY_ARRAY;
        private ArrayValue                advice          = Value.EMPTY_ARRAY;
        private Value                     resource        = Value.UNDEFINED;
        private List<Value>               documents       = new ArrayList<>();
        private List<Value>               retrievalErrors = new ArrayList<>();

        /**
         * Sets the PDP instance identifier.
         *
         * @param pdpId
         * the PDP identifier
         *
         * @return this builder
         */
        public Builder pdpId(String pdpId) {
            this.pdpId = pdpId;
            return this;
        }

        /**
         * Sets the configuration identifier.
         *
         * @param configurationId
         * the configuration version/ID
         *
         * @return this builder
         */
        public Builder configurationId(String configurationId) {
            this.configurationId = configurationId;
            return this;
        }

        /**
         * Sets the subscription identifier for correlation.
         *
         * @param subscriptionId
         * the subscription ID
         *
         * @return this builder
         */
        public Builder subscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
            return this;
        }

        /**
         * Sets the authorization subscription that was evaluated.
         *
         * @param subscription
         * the evaluated subscription
         *
         * @return this builder
         */
        public Builder subscription(AuthorizationSubscription subscription) {
            this.subscription = subscription;
            return this;
        }

        /**
         * Sets the timestamp when this decision was produced.
         *
         * @param timestamp
         * the decision timestamp in ISO-8601 format
         *
         * @return this builder
         */
        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the combining algorithm name in SAPL syntax.
         *
         * @param algorithm
         * the algorithm name (e.g., "deny-overrides")
         *
         * @return this builder
         */
        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Sets the combined decision.
         *
         * @param decision
         * the combined decision
         *
         * @return this builder
         */
        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        /**
         * Sets the total number of documents in the PRP for completeness proof.
         * <p>
         * This count includes all documents regardless of whether they matched,
         * allowing auditors to verify that all
         * documents were considered.
         *
         * @param totalDocuments
         * the total number of documents in the policy repository
         *
         * @return this builder
         */
        public Builder totalDocuments(int totalDocuments) {
            this.totalDocuments = totalDocuments;
            return this;
        }

        /**
         * Sets the merged obligations.
         *
         * @param obligations
         * the obligations array
         *
         * @return this builder
         */
        public Builder obligations(ArrayValue obligations) {
            this.obligations = obligations != null ? obligations : Value.EMPTY_ARRAY;
            return this;
        }

        /**
         * Sets the merged obligations from a list.
         *
         * @param obligations
         * the obligations list
         *
         * @return this builder
         */
        public Builder obligations(List<Value> obligations) {
            this.obligations = obligations != null && !obligations.isEmpty()
                    ? ArrayValue.builder().addAll(obligations).build()
                    : Value.EMPTY_ARRAY;
            return this;
        }

        /**
         * Sets the merged advice.
         *
         * @param advice
         * the advice array
         *
         * @return this builder
         */
        public Builder advice(ArrayValue advice) {
            this.advice = advice != null ? advice : Value.EMPTY_ARRAY;
            return this;
        }

        /**
         * Sets the merged advice from a list.
         *
         * @param advice
         * the advice list
         *
         * @return this builder
         */
        public Builder advice(List<Value> advice) {
            this.advice = advice != null && !advice.isEmpty() ? ArrayValue.builder().addAll(advice).build()
                    : Value.EMPTY_ARRAY;
            return this;
        }

        /**
         * Sets the resource transformation result.
         *
         * @param resource
         * the transformed resource or UNDEFINED
         *
         * @return this builder
         */
        public Builder resource(Value resource) {
            this.resource = resource != null ? resource : Value.UNDEFINED;
            return this;
        }

        /**
         * Adds a traced document decision (policy or policy set).
         *
         * @param tracedDocument
         * the TracedPolicyDecision or TracedPolicySetDecision Value
         *
         * @return this builder
         */
        public Builder addDocument(Value tracedDocument) {
            if (tracedDocument != null) {
                this.documents.add(tracedDocument);
            }
            return this;
        }

        /**
         * Adds multiple traced document decisions.
         *
         * @param tracedDocuments
         * collection of traced document Values
         *
         * @return this builder
         */
        public Builder documents(List<Value> tracedDocuments) {
            if (tracedDocuments != null) {
                this.documents.addAll(tracedDocuments);
            }
            return this;
        }

        /**
         * Adds a retrieval error that occurred during policy retrieval.
         * <p>
         * Retrieval errors occur when a document's target expression fails to evaluate,
         * preventing the document from
         * being properly matched against the subscription.
         *
         * @param documentName
         * the name of the document whose target failed
         * @param error
         * the ErrorValue describing the failure
         *
         * @return this builder
         */
        public Builder addRetrievalError(String documentName, ErrorValue error) {
            if (error != null) {
                val errorObj = ObjectValue.builder().put(TraceFields.NAME, Value.of(documentName))
                        .put(TraceFields.MESSAGE, Value.of(error.message())).build();
                this.retrievalErrors.add(errorObj);
            }
            return this;
        }

        /**
         * Adds a retrieval error from a pre-built error Value.
         *
         * @param retrievalError
         * the error Value
         *
         * @return this builder
         */
        public Builder addRetrievalError(Value retrievalError) {
            if (retrievalError != null) {
                this.retrievalErrors.add(retrievalError);
            }
            return this;
        }

        /**
         * Builds the TracedPdpDecision Value.
         *
         * @return an immutable ObjectValue representing the traced PDP decision
         */
        public Value build() {
            val traceBuilder = ObjectValue.builder().put(TraceFields.PDP_ID, Value.of(pdpId))
                    .put(TraceFields.CONFIGURATION_ID, Value.of(configurationId))
                    .put(TraceFields.SUBSCRIPTION_ID, Value.of(subscriptionId))
                    .put(TraceFields.SUBSCRIPTION, convertSubscription(subscription))
                    .put(TraceFields.TIMESTAMP, Value.of(timestamp)).put(TraceFields.ALGORITHM, Value.of(algorithm))
                    .put(TraceFields.TOTAL_DOCUMENTS, Value.of(totalDocuments))
                    .put(TraceFields.DOCUMENTS, ArrayValue.builder().addAll(documents).build());

            if (!retrievalErrors.isEmpty()) {
                traceBuilder.put(TraceFields.RETRIEVAL_ERRORS, ArrayValue.builder().addAll(retrievalErrors).build());
            }

            val resultBuilder = ObjectValue.builder().put(TraceFields.DECISION, Value.of(decision.name()))
                    .put(TraceFields.OBLIGATIONS, obligations).put(TraceFields.ADVICE, advice);

            // Only include resource if it has a defined value
            if (!Value.UNDEFINED.equals(resource)) {
                resultBuilder.put(TraceFields.RESOURCE, resource);
            }

            return resultBuilder.put(TraceFields.TRACE, traceBuilder.build()).build();
        }

        private Value convertSubscription(AuthorizationSubscription sub) {
            if (sub == null) {
                return Value.EMPTY_OBJECT;
            }
            val builder = ObjectValue.builder().put(SUBJECT, sub.subject()).put(ACTION, sub.action()).put(RESOURCE_EL,
                    sub.resource());
            // Only include environment if it has a defined value
            if (!Value.UNDEFINED.equals(sub.environment())) {
                builder.put(ENVIRONMENT, sub.environment());
            }
            return builder.build();
        }
    }

    /**
     * Extracts the decision from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the decision enum value
     */
    public static Decision getDecision(Value tracedPdp) {
        val text = getTextField(tracedPdp, TraceFields.DECISION);
        if (text == null) {
            return Decision.INDETERMINATE;
        }
        try {
            return Decision.valueOf(text);
        } catch (IllegalArgumentException e) {
            return Decision.INDETERMINATE;
        }
    }

    /**
     * Extracts obligations from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the obligations array
     */
    public static ArrayValue getObligations(Value tracedPdp) {
        return getArrayField(tracedPdp, TraceFields.OBLIGATIONS);
    }

    /**
     * Extracts advice from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the advice array
     */
    public static ArrayValue getAdvice(Value tracedPdp) {
        return getArrayField(tracedPdp, TraceFields.ADVICE);
    }

    /**
     * Extracts the resource from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the resource Value
     */
    public static Value getResource(Value tracedPdp) {
        if (tracedPdp instanceof ObjectValue obj) {
            val resource = obj.get(TraceFields.RESOURCE);
            return resource != null ? resource : Value.UNDEFINED;
        }
        return Value.UNDEFINED;
    }

    /**
     * Extracts the trace metadata object from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the trace ObjectValue
     */
    public static ObjectValue getTrace(Value tracedPdp) {
        if (tracedPdp instanceof ObjectValue obj) {
            val trace = obj.get(TraceFields.TRACE);
            if (trace instanceof ObjectValue traceObj) {
                return traceObj;
            }
        }
        return Value.EMPTY_OBJECT;
    }

    /**
     * Extracts the documents array from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the documents array containing TracedPolicyDecision or
     * TracedPolicySetDecision Values
     */
    public static ArrayValue getDocuments(Value tracedPdp) {
        val trace = getTrace(tracedPdp);
        val docs  = trace.get(TraceFields.DOCUMENTS);
        if (docs instanceof ArrayValue arr) {
            return arr;
        }
        return Value.EMPTY_ARRAY;
    }

    /**
     * Extracts the total documents count from a traced PDP decision.
     * <p>
     * This count represents all documents in the PRP, regardless of whether they
     * matched. Used for completeness proof
     * in auditing.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the total number of documents, or 0 if not present
     */
    public static int getTotalDocuments(Value tracedPdp) {
        val trace = getTrace(tracedPdp);
        val total = trace.get(TraceFields.TOTAL_DOCUMENTS);
        if (total instanceof NumberValue numberValue) {
            return numberValue.value().intValue();
        }
        return 0;
    }

    /**
     * Extracts the algorithm name from a traced PDP decision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the algorithm name in SAPL syntax
     */
    public static String getAlgorithm(Value tracedPdp) {
        val trace = getTrace(tracedPdp);
        val algo  = trace.get(TraceFields.ALGORITHM);
        if (algo instanceof TextValue text) {
            return text.value();
        }
        return null;
    }

    /**
     * Extracts retrieval errors from a traced PDP decision.
     * <p>
     * Retrieval errors occur when document target expressions fail to evaluate
     * during policy retrieval.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the retrieval errors array, or empty array if none
     */
    public static ArrayValue getRetrievalErrors(Value tracedPdp) {
        val trace  = getTrace(tracedPdp);
        val errors = trace.get(TraceFields.RETRIEVAL_ERRORS);
        if (errors instanceof ArrayValue arr) {
            return arr;
        }
        return Value.EMPTY_ARRAY;
    }

    /**
     * Checks if this traced PDP decision has retrieval errors.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return true if retrieval errors are present
     */
    public static boolean hasRetrievalErrors(Value tracedPdp) {
        return !getRetrievalErrors(tracedPdp).isEmpty();
    }

    /**
     * Converts a traced PDP decision Value to an AuthorizationDecision.
     *
     * @param tracedPdp
     * the traced PDP decision Value
     *
     * @return the extracted AuthorizationDecision
     */
    public static AuthorizationDecision toAuthorizationDecision(Value tracedPdp) {
        val decision    = getDecision(tracedPdp);
        val obligations = getObligations(tracedPdp);
        val advice      = getAdvice(tracedPdp);
        val resource    = getResource(tracedPdp);
        return new AuthorizationDecision(decision, obligations, advice, resource);
    }

    private static String getTextField(Value value, String fieldName) {
        if (value instanceof ObjectValue obj) {
            val field = obj.get(fieldName);
            if (field instanceof TextValue textValue) {
                return textValue.value();
            }
        }
        return null;
    }

    private static ArrayValue getArrayField(Value value, String fieldName) {
        if (value instanceof ObjectValue obj) {
            val field = obj.get(fieldName);
            if (field instanceof ArrayValue arrayValue) {
                return arrayValue;
            }
        }
        return Value.EMPTY_ARRAY;
    }
}
