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
package io.sapl.api.attributes;

import static io.sapl.api.shared.NameValidator.requireValidName;

import java.time.Duration;
import java.util.List;

import io.sapl.api.model.Value;
import lombok.NonNull;

public record AttributeFinderInvocation(
        @NonNull String configurationId,
        @NonNull String attributeName,
        Value entity,
        @NonNull List<Value> arguments,
        @NonNull Duration initialTimeOut,
        @NonNull Duration pollInterval,
        @NonNull Duration backoff,
        long retries,
        boolean fresh,
        @NonNull AttributeAccessContext ctx) {

    public AttributeFinderInvocation(@NonNull String configurationId,
            @NonNull String attributeName,
            @NonNull List<Value> arguments,
            @NonNull Duration initialTimeOut,
            @NonNull Duration pollInterval,
            @NonNull Duration backoff,
            long retries,
            boolean fresh,
            @NonNull AttributeAccessContext ctx) {
        this(configurationId, attributeName, null, arguments, initialTimeOut, pollInterval, backoff, retries, fresh,
                ctx);
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
