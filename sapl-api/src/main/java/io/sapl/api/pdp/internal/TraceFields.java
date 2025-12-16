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
package io.sapl.api.pdp.internal;

import lombok.experimental.UtilityClass;

/**
 * Field name constants for traced decision Value objects.
 * <p>
 * Centralizes all field names used in the hierarchical trace structure to
 * prevent typos and enable consistent access
 * across builders, accessors, and consumers.
 * <p>
 * The trace structure has three levels:
 * <ul>
 * <li>Level 1: TracedPolicyDecision - individual policy evaluation result</li>
 * <li>Level 2: TracedPolicySetDecision - combining result within a policy
 * set</li>
 * <li>Level 3: TracedPdpDecision - PDP level combining with full metadata</li>
 * </ul>
 */
@UtilityClass
public class TraceFields {

    // Common decision fields (used at all levels)
    public static final String DECISION    = "decision";
    public static final String OBLIGATIONS = "obligations";
    public static final String ADVICE      = "advice";
    public static final String RESOURCE    = "resource";

    // Policy/document identification
    public static final String NAME        = "name";
    public static final String ENTITLEMENT = "entitlement";
    public static final String TYPE        = "type";

    // Document type values
    public static final String TYPE_SET    = "set";
    public static final String TYPE_POLICY = "policy";

    // Combining algorithm
    public static final String ALGORITHM = "algorithm";

    // Nested collections
    public static final String POLICIES  = "policies";
    public static final String DOCUMENTS = "documents";

    // Attribute tracking
    public static final String ATTRIBUTES = "attributes";
    public static final String ERRORS     = "errors";

    // Attribute record fields
    public static final String INVOCATION = "invocation";
    public static final String VALUE      = "value";
    public static final String TIMESTAMP  = "timestamp";
    public static final String LOCATION   = "location";

    // Invocation fields
    public static final String ATTRIBUTE_NAME   = "attributeName";
    public static final String ENTITY           = "entity";
    public static final String ARGUMENTS        = "arguments";
    public static final String CONFIGURATION_ID = "configurationId";
    public static final String IS_ENVIRONMENT   = "isEnvironment";
    public static final String FRESH            = "fresh";
    public static final String INITIAL_TIMEOUT  = "initialTimeoutMs";
    public static final String POLL_INTERVAL    = "pollIntervalMs";
    public static final String BACKOFF          = "backoffMs";
    public static final String RETRIES          = "retries";

    // Source location fields
    public static final String DOCUMENT_NAME   = "documentName";
    public static final String DOCUMENT_SOURCE = "documentSource";
    public static final String START           = "start";
    public static final String END             = "end";
    public static final String LINE            = "line";

    // PDP-level trace fields
    public static final String PDP_ID           = "pdpId";
    public static final String SUBSCRIPTION_ID  = "subscriptionId";
    public static final String SUBSCRIPTION     = "subscription";
    public static final String TRACE            = "trace";
    public static final String MODIFICATIONS    = "modifications";
    public static final String RETRIEVAL_ERRORS = "retrievalErrors";

    // Error tracking fields
    public static final String TARGET_ERROR = "targetError";
    public static final String MESSAGE      = "message";

    // Target matching (for first-applicable order evidence)
    public static final String TARGET_MATCH = "targetMatch";

    // Aggregate counts for completeness proof
    public static final String TOTAL_POLICIES  = "totalPolicies";
    public static final String TOTAL_DOCUMENTS = "totalDocuments";

    // Coverage tracking fields (COVERAGE trace level)
    public static final String CONDITIONS    = "conditions";
    public static final String STATEMENT_ID  = "statementId";
    public static final String RESULT        = "result";
    public static final String TARGET_RESULT = "targetResult";

    // Condition position fields (for precise coverage highlighting)
    public static final String START_LINE = "startLine";
    public static final String END_LINE   = "endLine";
    public static final String START_CHAR = "startChar";
    public static final String END_CHAR   = "endChar";

    // Target expression position fields (for coverage highlighting)
    public static final String TARGET_START_LINE = "targetStartLine";
    public static final String TARGET_END_LINE   = "targetEndLine";
    public static final String TARGET_START_CHAR = "targetStartChar";
    public static final String TARGET_END_CHAR   = "targetEndChar";
}
