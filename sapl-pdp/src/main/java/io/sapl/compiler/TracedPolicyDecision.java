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
package io.sapl.compiler;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.traced.AttributeRecord;
import io.sapl.api.pdp.traced.ConditionHit;
import io.sapl.api.pdp.traced.TraceFields;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builder and utilities for Level 1 traced decisions: individual policy
 * evaluation results.
 * <p>
 * A TracedPolicyDecision captures the complete result of evaluating a single
 * matched policy, including the
 * authorization decision, constraints, attribute invocations, and any errors.
 * <p>
 * Structure:
 *
 * <pre>
 * {
 *   "name": "policy-name",
 *   "entitlement": "PERMIT",
 *   "decision": "PERMIT",
 *   "obligations": [...],
 *   "advice": [...],
 *   "resource": {...},
 *   "attributes": [...],
 *   "errors": [...]
 * }
 * </pre>
 */
@UtilityClass
public class TracedPolicyDecision {

    /**
     * Creates a new builder for constructing a TracedPolicyDecision Value.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a minimal trace for a policy whose target did not match.
     * <p>
     * Used by first-applicable algorithm to provide order evidence. The trace
     * contains only the policy name, type, and
     * targetMatch=false indicator.
     *
     * @param policyName
     * the name of the policy that did not match
     *
     * @return a minimal ObjectValue trace
     */
    public static Value createNoMatchTrace(String policyName, String entitlement) {
        return ObjectValue.builder().put(TraceFields.NAME, Value.of(policyName))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                .put(TraceFields.ENTITLEMENT, Value.of(entitlement))
                .put(TraceFields.DECISION, Value.of(Decision.NOT_APPLICABLE.name()))
                .put(TraceFields.TARGET_MATCH, Value.FALSE).build();
    }

    /**
     * Creates a coverage-enabled trace for a policy whose target did not match.
     * <p>
     * Unlike createNoMatchTrace, this includes full position data for the target
     * expression to enable precise coverage highlighting. Used when COVERAGE trace
     * level is enabled.
     *
     * @param policyName
     * the name of the policy that did not match
     * @param targetLocation
     * the source location of the target expression (may be null)
     *
     * @return an ObjectValue trace with position data for coverage
     */
    public static Value createNoMatchCoverageTrace(String policyName, String entitlement,
            SourceLocation targetLocation) {
        val builder = ObjectValue.builder().put(TraceFields.NAME, Value.of(policyName))
                .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                .put(TraceFields.ENTITLEMENT, Value.of(entitlement))
                .put(TraceFields.DECISION, Value.of(Decision.NOT_APPLICABLE.name()))
                .put(TraceFields.TARGET_MATCH, Value.FALSE).put(TraceFields.TARGET_RESULT, Value.FALSE);
        if (targetLocation != null) {
            builder.put(TraceFields.TARGET_START_LINE, Value.of(targetLocation.line()))
                    .put(TraceFields.TARGET_END_LINE, Value.of(targetLocation.endLine()))
                    .put(TraceFields.TARGET_START_CHAR, Value.of(targetLocation.start()))
                    .put(TraceFields.TARGET_END_CHAR, Value.of(targetLocation.end()));
        }
        return builder.build();
    }

    /**
     * Builder for fluent TracedPolicyDecision construction.
     * <p>
     * Supports constant folding: when all inputs are known at compile time, the
     * built Value can be used as a constant.
     */
    public static final class Builder {
        private String         name;
        private String         entitlement;
        private Decision       decision;
        private ArrayValue     obligations = Value.EMPTY_ARRAY;
        private ArrayValue     advice      = Value.EMPTY_ARRAY;
        private Value          resource    = Value.UNDEFINED;
        private List<Value>    attributes  = new ArrayList<>();
        private List<Value>    errors      = new ArrayList<>();
        private Value          targetError;
        private List<Value>    conditions  = new ArrayList<>();
        private Boolean        targetResult;
        private SourceLocation targetLocation;
        private SourceLocation policyLocation;
        private Boolean        hasConditions;

        /**
         * Sets the policy name.
         *
         * @param name
         * the policy name from SAPL source
         *
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the declared entitlement (PERMIT or DENY).
         *
         * @param entitlement
         * the policy's declared entitlement
         *
         * @return this builder
         */
        public Builder entitlement(String entitlement) {
            this.entitlement = entitlement;
            return this;
        }

        /**
         * Sets the evaluated decision.
         *
         * @param decision
         * the decision after policy evaluation
         *
         * @return this builder
         */
        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        /**
         * Sets obligations from an ArrayValue.
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
         * Sets advice from an ArrayValue.
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
         * Adds an attribute record as a Value.
         *
         * @param attribute
         * the attribute Value (converted from AttributeRecord)
         *
         * @return this builder
         */
        public Builder addAttribute(Value attribute) {
            this.attributes.add(attribute);
            return this;
        }

        /**
         * Adds multiple attribute Values.
         *
         * @param attributes
         * collection of attribute Values
         *
         * @return this builder
         */
        public Builder attributes(Collection<Value> attributes) {
            if (attributes != null) {
                this.attributes.addAll(attributes);
            }
            return this;
        }

        /**
         * Adds an error Value.
         *
         * @param error
         * the error Value
         *
         * @return this builder
         */
        public Builder addError(Value error) {
            this.errors.add(error);
            return this;
        }

        /**
         * Adds multiple error Values.
         *
         * @param errors
         * collection of error Values
         *
         * @return this builder
         */
        public Builder errors(Collection<Value> errors) {
            if (errors != null) {
                this.errors.addAll(errors);
            }
            return this;
        }

        /**
         * Sets a target expression error that caused this policy to be INDETERMINATE.
         * <p>
         * When a target expression fails to evaluate (e.g., type error, division by
         * zero), this captures the error
         * details for audit and debugging purposes.
         *
         * @param error
         * the ErrorValue from target expression evaluation
         *
         * @return this builder
         */
        public Builder targetError(ErrorValue error) {
            if (error != null) {
                this.targetError = convertTargetError(error);
            }
            return this;
        }

        private Value convertTargetError(ErrorValue error) {
            val builder = ObjectValue.builder().put(TraceFields.MESSAGE, Value.of(error.message()));
            if (error.location() != null) {
                builder.put(TraceFields.LOCATION,
                        ObjectValue.builder().put(TraceFields.LINE, Value.of(error.location().line()))
                                .put(TraceFields.START, Value.of(error.location().start()))
                                .put(TraceFields.END, Value.of(error.location().end())).build());
            }
            return builder.build();
        }

        /**
         * Adds a condition hit for coverage tracking (COVERAGE trace level only).
         *
         * @param conditionHit
         * the condition hit to add
         *
         * @return this builder
         */
        public Builder addCondition(ConditionHit conditionHit) {
            this.conditions.add(conditionHit.toValue());
            return this;
        }

        /**
         * Adds all condition hits for coverage tracking (COVERAGE trace level only).
         *
         * @param conditionHits
         * the condition hits to add
         *
         * @return this builder
         */
        public Builder conditions(List<ConditionHit> conditionHits) {
            if (conditionHits != null) {
                for (val hit : conditionHits) {
                    this.conditions.add(hit.toValue());
                }
            }
            return this;
        }

        /**
         * Sets the target expression result for coverage tracking (COVERAGE trace level
         * only).
         *
         * @param matched
         * true if target matched, false if it did not
         *
         * @return this builder
         */
        public Builder targetResult(boolean matched) {
            this.targetResult = matched;
            return this;
        }

        /**
         * Sets the target expression source location for coverage tracking (COVERAGE
         * trace level only).
         *
         * @param location
         * the source location of the target expression
         *
         * @return this builder
         */
        public Builder targetLocation(SourceLocation location) {
            this.targetLocation = location;
            return this;
        }

        /**
         * Sets the policy declaration source location for coverage tracking (COVERAGE
         * trace level only).
         *
         * @param location
         * the source location of the policy declaration (policy "name" permit/deny)
         *
         * @return this builder
         */
        public Builder policyLocation(SourceLocation location) {
            this.policyLocation = location;
            return this;
        }

        /**
         * Sets whether this policy has where-clause conditions for coverage tracking
         * (COVERAGE trace level only).
         * <p>
         * This determines branch semantics: policies without conditions are
         * single-branch
         * (just need to be hit), while policies with conditions are two-branch (need
         * both
         * entitlement-returned and NOT_APPLICABLE outcomes for full coverage).
         *
         * @param hasConditions
         * true if the policy has where-clause conditions
         *
         * @return this builder
         */
        public Builder hasConditions(boolean hasConditions) {
            this.hasConditions = hasConditions;
            return this;
        }

        /**
         * Extracts decision components from a raw decision Value and populates this
         * builder.
         * <p>
         * The decision Value is expected to have the structure produced by
         * AuthorizationDecisionUtil.buildDecision().
         *
         * @param decisionValue
         * the raw decision Value from policy evaluation
         *
         * @return this builder
         */
        public Builder fromDecisionValue(Value decisionValue) {
            if (decisionValue instanceof ObjectValue decisionObj) {
                this.decision    = extractDecision(decisionObj);
                this.obligations = extractArrayField(decisionObj, AuthorizationDecisionUtil.FIELD_OBLIGATIONS);
                this.advice      = extractArrayField(decisionObj, AuthorizationDecisionUtil.FIELD_ADVICE);
                this.resource    = decisionObj.get(AuthorizationDecisionUtil.FIELD_RESOURCE);
                if (this.resource == null) {
                    this.resource = Value.UNDEFINED;
                }
            } else {
                this.decision = Decision.INDETERMINATE;
            }
            return this;
        }

        private Decision extractDecision(ObjectValue decisionObj) {
            val decisionField = decisionObj.get(AuthorizationDecisionUtil.FIELD_DECISION);
            if (decisionField instanceof TextValue textValue) {
                try {
                    return Decision.valueOf(textValue.value());
                } catch (IllegalArgumentException e) {
                    return Decision.INDETERMINATE;
                }
            }
            return Decision.INDETERMINATE;
        }

        private ArrayValue extractArrayField(ObjectValue obj, String fieldName) {
            val field = obj.get(fieldName);
            if (field instanceof ArrayValue arrayValue) {
                return arrayValue;
            }
            return Value.EMPTY_ARRAY;
        }

        /**
         * Builds the TracedPolicyDecision Value.
         *
         * @return an immutable ObjectValue representing the traced policy decision
         */
        public Value build() {
            val builder = ObjectValue.builder().put(TraceFields.NAME, Value.of(name))
                    .put(TraceFields.TYPE, Value.of(TraceFields.TYPE_POLICY))
                    .put(TraceFields.ENTITLEMENT, Value.of(entitlement))
                    .put(TraceFields.DECISION, Value.of(decision.name())).put(TraceFields.OBLIGATIONS, obligations)
                    .put(TraceFields.ADVICE, advice).put(TraceFields.RESOURCE, resource)
                    .put(TraceFields.ATTRIBUTES, ArrayValue.builder().addAll(attributes).build())
                    .put(TraceFields.ERRORS, ArrayValue.builder().addAll(errors).build());

            if (targetError != null) {
                builder.put(TraceFields.TARGET_ERROR, targetError);
            }

            // Coverage fields (only present when COVERAGE trace level enabled)
            if (!conditions.isEmpty()) {
                builder.put(TraceFields.CONDITIONS, ArrayValue.builder().addAll(conditions).build());
            }
            if (targetResult != null) {
                builder.put(TraceFields.TARGET_RESULT, Value.of(targetResult));
            }
            if (targetLocation != null) {
                builder.put(TraceFields.TARGET_START_LINE, Value.of(targetLocation.line()))
                        .put(TraceFields.TARGET_END_LINE, Value.of(targetLocation.endLine()))
                        .put(TraceFields.TARGET_START_CHAR, Value.of(targetLocation.start()))
                        .put(TraceFields.TARGET_END_CHAR, Value.of(targetLocation.end()));
            }
            if (policyLocation != null) {
                builder.put(TraceFields.POLICY_START_LINE, Value.of(policyLocation.line()))
                        .put(TraceFields.POLICY_END_LINE, Value.of(policyLocation.endLine()))
                        .put(TraceFields.POLICY_START_CHAR, Value.of(policyLocation.start()))
                        .put(TraceFields.POLICY_END_CHAR, Value.of(policyLocation.end()));
            }
            if (hasConditions != null) {
                builder.put(TraceFields.HAS_CONDITIONS, Value.of(hasConditions));
            }

            return builder.build();
        }
    }

    // ========== Accessor Methods ==========

    /**
     * Extracts the policy name from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the policy name
     */
    public static String getName(Value tracedPolicy) {
        return getTextField(tracedPolicy, TraceFields.NAME);
    }

    /**
     * Extracts the entitlement from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the entitlement string
     */
    public static String getEntitlement(Value tracedPolicy) {
        return getTextField(tracedPolicy, TraceFields.ENTITLEMENT);
    }

    /**
     * Extracts the decision from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the decision enum value
     */
    public static Decision getDecision(Value tracedPolicy) {
        val text = getTextField(tracedPolicy, TraceFields.DECISION);
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
     * Extracts obligations from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the obligations array
     */
    public static ArrayValue getObligations(Value tracedPolicy) {
        return getArrayField(tracedPolicy, TraceFields.OBLIGATIONS);
    }

    /**
     * Extracts advice from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the advice array
     */
    public static ArrayValue getAdvice(Value tracedPolicy) {
        return getArrayField(tracedPolicy, TraceFields.ADVICE);
    }

    /**
     * Extracts the resource from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the resource Value
     */
    public static Value getResource(Value tracedPolicy) {
        if (tracedPolicy instanceof ObjectValue obj) {
            val resource = obj.get(TraceFields.RESOURCE);
            return resource != null ? resource : Value.UNDEFINED;
        }
        return Value.UNDEFINED;
    }

    /**
     * Extracts attributes from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the attributes array
     */
    public static ArrayValue getAttributes(Value tracedPolicy) {
        return getArrayField(tracedPolicy, TraceFields.ATTRIBUTES);
    }

    /**
     * Extracts errors from a traced policy decision.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the errors array
     */
    public static ArrayValue getErrors(Value tracedPolicy) {
        return getArrayField(tracedPolicy, TraceFields.ERRORS);
    }

    /**
     * Extracts target error from a traced policy decision if present.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the target error ObjectValue, or null if no target error occurred
     */
    public static ObjectValue getTargetError(Value tracedPolicy) {
        if (tracedPolicy instanceof ObjectValue obj) {
            val field = obj.get(TraceFields.TARGET_ERROR);
            if (field instanceof ObjectValue targetErr) {
                return targetErr;
            }
        }
        return null;
    }

    /**
     * Checks if this traced policy decision has a target error.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return true if a target error is present
     */
    public static boolean hasTargetError(Value tracedPolicy) {
        return getTargetError(tracedPolicy) != null;
    }

    private static Boolean getTargetMatch(Value tracedPolicy) {
        if (tracedPolicy instanceof ObjectValue obj) {
            val field = obj.get(TraceFields.TARGET_MATCH);
            if (field instanceof BooleanValue boolValue) {
                return boolValue.value();
            }
        }
        return null;
    }

    /**
     * Checks if this is a non-matching policy trace (created via
     * createNoMatchTrace).
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return true if this is a non-matching policy trace
     */
    public static boolean isNoMatchTrace(Value tracedPolicy) {
        return Boolean.FALSE.equals(getTargetMatch(tracedPolicy));
    }

    // ========== Coverage Accessor Methods (COVERAGE trace level) ==========

    /**
     * Extracts condition hits from a traced policy decision.
     * <p>
     * Only present when COVERAGE trace level is enabled.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return the conditions array, or empty array if not present
     */
    public static ArrayValue getConditions(Value tracedPolicy) {
        return getArrayField(tracedPolicy, TraceFields.CONDITIONS);
    }

    /**
     * Checks if this traced policy decision has condition hits recorded.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return true if conditions are present
     */
    public static boolean hasConditions(Value tracedPolicy) {
        return !getConditions(tracedPolicy).isEmpty();
    }

    /**
     * Extracts the target result from a traced policy decision.
     * <p>
     * Only present when COVERAGE trace level is enabled.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return true if target matched, false if not, null if not recorded
     */
    public static Boolean getTargetResult(Value tracedPolicy) {
        if (tracedPolicy instanceof ObjectValue obj) {
            val field = obj.get(TraceFields.TARGET_RESULT);
            if (field instanceof BooleanValue boolValue) {
                return boolValue.value();
            }
        }
        return null;
    }

    /**
     * Checks if this traced policy decision has target result recorded.
     *
     * @param tracedPolicy
     * the traced policy decision Value
     *
     * @return true if target result is present
     */
    public static boolean hasTargetResult(Value tracedPolicy) {
        return getTargetResult(tracedPolicy) != null;
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

    // ========== AttributeRecord Conversion ==========

    /**
     * Converts an AttributeRecord POJO to a Value for inclusion in the trace.
     *
     * @param attributeRecord
     * the AttributeRecord to convert
     *
     * @return an ObjectValue representing the attribute attributeRecord
     */
    public static Value convertAttributeRecord(AttributeRecord attributeRecord) {
        val builder = ObjectValue.builder().put(TraceFields.INVOCATION, convertInvocation(attributeRecord.invocation()))
                .put(TraceFields.VALUE, attributeRecord.attributeValue())
                .put(TraceFields.TIMESTAMP, Value.of(attributeRecord.retrievedAt().toString()));

        if (attributeRecord.location() != null) {
            builder.put(TraceFields.LOCATION, convertSourceLocation(attributeRecord.location()));
        }

        return builder.build();
    }

    /**
     * Converts a list of AttributeRecord POJOs to Values.
     *
     * @param records
     * the records to convert
     *
     * @return list of converted Values
     */
    public static List<Value> convertAttributeRecords(List<AttributeRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(TracedPolicyDecision::convertAttributeRecord).toList();
    }

    private static Value convertInvocation(AttributeFinderInvocation invocation) {
        val builder = ObjectValue.builder().put(TraceFields.ATTRIBUTE_NAME, Value.of(invocation.attributeName()))
                .put(TraceFields.CONFIGURATION_ID, Value.of(invocation.configurationId()))
                .put(TraceFields.IS_ENVIRONMENT, Value.of(invocation.isEnvironmentAttributeInvocation()))
                .put(TraceFields.FRESH, Value.of(invocation.fresh()))
                .put(TraceFields.INITIAL_TIMEOUT, Value.of(invocation.initialTimeOut().toMillis()))
                .put(TraceFields.POLL_INTERVAL, Value.of(invocation.pollInterval().toMillis()))
                .put(TraceFields.BACKOFF, Value.of(invocation.backoff().toMillis()))
                .put(TraceFields.RETRIES, Value.of(invocation.retries()));

        if (invocation.entity() != null) {
            builder.put(TraceFields.ENTITY, invocation.entity());
        }

        if (!invocation.arguments().isEmpty()) {
            builder.put(TraceFields.ARGUMENTS, ArrayValue.builder().addAll(invocation.arguments()).build());
        }

        return builder.build();
    }

    private static Value convertSourceLocation(SourceLocation location) {
        val builder = ObjectValue.builder().put(TraceFields.LINE, Value.of(location.line()))
                .put(TraceFields.START, Value.of(location.start())).put(TraceFields.END, Value.of(location.end()));

        if (location.documentName() != null) {
            builder.put(TraceFields.DOCUMENT_NAME, Value.of(location.documentName()));
        }

        return builder.build();
    }

    // ========== Error Extraction ==========

    /**
     * Extracts error Values from a decision Value's structure.
     * <p>
     * Errors can occur in:
     * <ul>
     * <li>The decision value itself (if it's an ErrorValue)</li>
     * <li>The errors array field (from body evaluation errors)</li>
     * <li>Obligations array (ErrorValue elements)</li>
     * <li>Advice array (ErrorValue elements)</li>
     * <li>Resource transformation (if ErrorValue)</li>
     * </ul>
     *
     * @param decisionValue
     * the decision Value to extract errors from
     *
     * @return list of error Values found
     */
    public static List<Value> extractErrors(Value decisionValue) {
        val errors = new ArrayList<Value>();

        if (decisionValue instanceof ErrorValue errorValue) {
            errors.add(errorValue);
            return errors;
        }

        if (decisionValue instanceof ObjectValue obj) {
            // Extract errors from the errors field (body evaluation errors)
            extractErrorsFromArray(obj.get(AuthorizationDecisionUtil.FIELD_ERRORS), errors);
            // Extract ErrorValue elements from obligations
            extractErrorsFromArray(obj.get(AuthorizationDecisionUtil.FIELD_OBLIGATIONS), errors);
            // Extract ErrorValue elements from advice
            extractErrorsFromArray(obj.get(AuthorizationDecisionUtil.FIELD_ADVICE), errors);

            val resource = obj.get(AuthorizationDecisionUtil.FIELD_RESOURCE);
            if (resource instanceof ErrorValue errorValue) {
                errors.add(errorValue);
            }
        }

        return errors;
    }

    private static void extractErrorsFromArray(Value arrayValue, List<Value> errors) {
        if (arrayValue instanceof ArrayValue arr) {
            for (val element : arr) {
                if (element instanceof ErrorValue errorValue) {
                    errors.add(errorValue);
                }
            }
        }
    }
}
