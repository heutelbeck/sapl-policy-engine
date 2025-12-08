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
package io.sapl.api.attributes;

import io.sapl.api.model.Value;
import lombok.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * Encapsulates all parameters for an attribute finder invocation during policy
 * evaluation.
 * <p>
 * An invocation captures the complete context needed to execute an attribute
 * finder, including the target entity,
 * arguments, runtime options, and resilience settings. The AttributeBroker uses
 * the invocation as a cache key to decide
 * whether to reuse existing streams or create new ones.
 * <p>
 * <b>Key Concepts:</b>
 * <ul>
 * <li><b>Entity</b>: The value preceding the attribute (e.g., in
 * {@code user.<pip.attr>}, {@code user} is the entity).
 * Null for environment attributes like {@code <time.now>}.</li>
 * <li><b>Arguments</b>: Additional parameters passed to the attribute finder
 * (e.g., in {@code <pip.attr(arg1, arg2)>},
 * {@code [arg1, arg2]} are arguments).</li>
 * <li><b>Fresh</b>: When {@code true}, forces creation of a new stream
 * bypassing the cache.</li>
 * <li><b>Variables</b>: Runtime context from the policy evaluation (subject,
 * resource, environment, etc.).</li>
 * </ul>
 * <p>
 * <b>Equality and Caching:</b> Two invocations are equal if all fields match.
 * The {@code fresh} flag is part of the
 * equality check, so {@code fresh=true} creates a different cache key than
 * {@code fresh=false}.
 *
 * @param configurationId
 * identifier for the PDP configuration
 * @param attributeName
 * fully qualified attribute name (e.g., "time.now")
 * @param entity
 * the entity value for entity attributes, or null for environment attributes
 * @param arguments
 * list of argument values passed to the attribute finder
 * @param variables
 * runtime variables from policy evaluation context
 * @param initialTimeOut
 * timeout for the first value emission
 * @param pollInterval
 * interval for re-polling when attribute stream completes
 * @param backoff
 * delay between retry attempts on error
 * @param retries
 * number of retry attempts before failing
 * @param fresh
 * if true, bypass stream cache and create new stream
 *
 * @see AttributeBroker#attributeStream(AttributeFinderInvocation)
 * @see AttributeFinder#invoke(AttributeFinderInvocation)
 */
public record AttributeFinderInvocation(
        @NonNull String configurationId,
        @NonNull String attributeName,
        Value entity,
        @NonNull List<Value> arguments,
        @NonNull Map<String, Value> variables,
        @NonNull Duration initialTimeOut,
        @NonNull Duration pollInterval,
        @NonNull Duration backoff,
        long retries,
        boolean fresh) {

    /**
     * Creates an environment attribute invocation (entity is null).
     *
     * @param configurationId
     * identifier for the PDP configuration
     * @param attributeName
     * fully qualified attribute name
     * @param arguments
     * list of argument values
     * @param variables
     * runtime variables from policy evaluation
     * @param initialTimeOut
     * timeout for first value
     * @param pollInterval
     * interval for re-polling
     * @param backoff
     * delay between retries
     * @param retries
     * number of retry attempts
     * @param fresh
     * bypass stream cache if true
     */
    public AttributeFinderInvocation(@NonNull String configurationId,
            @NonNull String attributeName,
            @NonNull List<Value> arguments,
            @NonNull Map<String, Value> variables,
            @NonNull Duration initialTimeOut,
            @NonNull Duration pollInterval,
            @NonNull Duration backoff,
            long retries,
            boolean fresh) {
        this(configurationId, attributeName, null, arguments, variables, initialTimeOut, pollInterval, backoff, retries,
                fresh);
    }

    public AttributeFinderInvocation {
        requireValidName(attributeName);
    }

    /**
     * Checks if this invocation is for an environment attribute.
     * <p>
     * Environment attributes like {@code <time.now>} have no entity (entity is
     * null), while entity attributes like
     * {@code user.<pip.role>} have an entity value.
     *
     * @return true if entity is null (environment attribute), false otherwise
     */
    public boolean isEnvironmentAttributeInvocation() {
        return null == entity;
    }

}
