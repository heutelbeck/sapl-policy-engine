/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.api;

import static io.sapl.validation.NameValidator.requireValidName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;

public record AttributeFinderInvocation(@NonNull String pdpConfigurationId, @NonNull String fullyQualifiedAttributeName,
        Val entity, @NonNull List<Val> arguments, @NonNull Map<String, Val> variables, @NonNull Duration initialTimeOut,
        @NonNull Duration pollIntervall, @NonNull Duration backoff, long retries, boolean fresh) {

    public AttributeFinderInvocation(@NonNull String pdpConfigurationId, @NonNull String fullyQualifiedAttributeName,
            @NonNull List<Val> arguments, @NonNull Map<String, Val> variables, @NonNull Duration initialTimeOut,
            @NonNull Duration pollIntervall, @NonNull Duration backoff, long retries, boolean fresh) {
        this(pdpConfigurationId, fullyQualifiedAttributeName, null, arguments, variables, initialTimeOut, pollIntervall,
                backoff, retries, fresh);
    }

    public AttributeFinderInvocation {
        requireValidName(fullyQualifiedAttributeName);
    }

}
