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
package io.sapl.api.pdp.traced;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder and utilities for Level 2 traced decisions: policy set combining
 * results.
 * <p>
 * A TracedPolicySetDecision captures the result of combining multiple policies
 * within a policy set, including the
 * combined authorization decision, merged constraints, aggregate counts for
 * completeness proof, and the individual
 * policy traces.
 * <p>
 * Structure:
 *
 * <pre>
 * {
 *   "name": "set-name",
 *   "type": "set",
 *   "algorithm": "deny-overrides",
 *   "decision": "PERMIT",
 *   "totalPolicies": 10,
 *   "obligations": [...],
 *   "advice": [...],
 *   "resource": ...,
 *   "policies": [
 *     { TracedPolicyDecision },
 *     { TracedPolicyDecision }
 *   ]
 * }
 * </pre>
 */
@UtilityClass
public class TracedPolicySetDecision {

    /**
     * Creates a new builder for constructing a TracedPolicySetDecision Value.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for fluent TracedPolicySetDecision construction.
     */
    public static final class Builder {
        private String      name;
        private String      algorithm;
        private Decision    decision;
        private int         totalPolicies;
        private ArrayValue  obligations = Value.EMPTY_ARRAY;
        private ArrayValue  advice      = Value.EMPTY_ARRAY;
        private Value       resource    = Value.UNDEFINED;
        private List<Value> policies    = new ArrayList<>();

        /**
         * Sets the policy set name.
         *
         * @param name
         * the policy set name from SAPL source
         *
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
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
         * Sets the total number of policies in the set for completeness proof.
         * <p>
         * This count includes all policies regardless of whether they matched, allowing
         * auditors to verify that all
         * policies were considered.
         *
         * @param totalPolicies
         * the total number of policies in the policy set
         *
         * @return this builder
         */
        public Builder totalPolicies(int totalPolicies) {
            this.totalPolicies = totalPolicies;
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
         * Adds a traced policy decision to the policies array.
         *
         * @param tracedPolicy
         * the TracedPolicyDecision Value
         *
         * @return this builder
         */
        public Builder addPolicy(Value tracedPolicy) {
            if (tracedPolicy != null) {
                this.policies.add(tracedPolicy);
            }
            return this;
        }

        /**
         * Adds multiple traced policy decisions.
         *
         * @param tracedPolicies
         * collection of TracedPolicyDecision Values
         *
         * @return this builder
         */
        public Builder policies(List<Value> tracedPolicies) {
            if (tracedPolicies != null) {
                this.policies.addAll(tracedPolicies);
            }
            return this;
        }

        /**
         * Builds the TracedPolicySetDecision Value.
         *
         * @return an immutable ObjectValue representing the traced policy set decision
         */
        public Value build() {
            return ObjectValue.builder().put(TraceFields.NAME, Value.of(name))
                    .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_SET))
                    .put(TraceFields.ALGORITHM, Value.of(algorithm))
                    .put(TraceFields.DECISION, Value.of(decision.name()))
                    .put(TraceFields.TOTAL_POLICIES, Value.of(totalPolicies)).put(TraceFields.OBLIGATIONS, obligations)
                    .put(TraceFields.ADVICE, advice).put(TraceFields.RESOURCE, resource)
                    .put(TraceFields.POLICIES, ArrayValue.builder().addAll(policies).build()).build();
        }
    }

    // ========== Accessor Methods ==========

    /**
     * Extracts the policy set name from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the policy set name
     */
    public static String getName(Value tracedSet) {
        return getTextField(tracedSet, TraceFields.NAME);
    }

    /**
     * Extracts the combining algorithm name from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the algorithm name in SAPL syntax
     */
    public static String getAlgorithm(Value tracedSet) {
        return getTextField(tracedSet, TraceFields.ALGORITHM);
    }

    /**
     * Extracts the decision from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the decision enum value
     */
    public static Decision getDecision(Value tracedSet) {
        val text = getTextField(tracedSet, TraceFields.DECISION);
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
     * Extracts obligations from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the obligations array
     */
    public static ArrayValue getObligations(Value tracedSet) {
        return getArrayField(tracedSet, TraceFields.OBLIGATIONS);
    }

    /**
     * Extracts advice from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the advice array
     */
    public static ArrayValue getAdvice(Value tracedSet) {
        return getArrayField(tracedSet, TraceFields.ADVICE);
    }

    /**
     * Extracts the resource from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the resource Value
     */
    public static Value getResource(Value tracedSet) {
        if (tracedSet instanceof ObjectValue obj) {
            val resource = obj.get(TraceFields.RESOURCE);
            return resource != null ? resource : Value.UNDEFINED;
        }
        return Value.UNDEFINED;
    }

    /**
     * Extracts the policies array from a traced set decision.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the policies array containing TracedPolicyDecision Values
     */
    public static ArrayValue getPolicies(Value tracedSet) {
        return getArrayField(tracedSet, TraceFields.POLICIES);
    }

    /**
     * Extracts the total policies count from a traced set decision.
     * <p>
     * This count represents all policies in the set, regardless of whether they
     * matched. Used for completeness proof in
     * auditing.
     *
     * @param tracedSet
     * the traced policy set decision Value
     *
     * @return the total number of policies, or 0 if not present
     */
    public static int getTotalPolicies(Value tracedSet) {
        if (tracedSet instanceof ObjectValue obj) {
            val field = obj.get(TraceFields.TOTAL_POLICIES);
            if (field instanceof NumberValue numberValue) {
                return numberValue.value().intValue();
            }
        }
        return 0;
    }

    /**
     * Checks if a traced decision is a policy set (type == "set").
     *
     * @param traced
     * the traced decision Value
     *
     * @return true if it's a policy set decision
     */
    public static boolean isPolicySet(Value traced) {
        return TraceFields.TYPE_SET.equals(getTextField(traced, TraceFields.TYPE));
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
