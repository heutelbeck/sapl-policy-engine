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
package io.sapl.attributes.broker.api.sub;

import static io.sapl.validation.NameValidator.requireValidName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;

public record AttributeSubscription(
        @NonNull String pdpConfigurationId,
        EObject source,
        @NonNull String attributeNameReference,
        Val entity,
        @NonNull List<Val> arguments,
        @NonNull Map<String, Val> variables,
        @NonNull Duration initialTimeOut,
        @NonNull Duration pollIntervall,
        @NonNull Duration backoff,
        long retries,
        boolean fresh) {

    public AttributeSubscription(@NonNull String pdpConfigurationId,
            EObject source,
            @NonNull String attributeNameReference,
            @NonNull List<Val> arguments,
            @NonNull Map<String, Val> variables,
            @NonNull Duration initialTimeOut,
            @NonNull Duration pollIntervall,
            @NonNull Duration backoff,
            long retries,
            boolean fresh) {
        this(pdpConfigurationId, source, attributeNameReference, null, arguments, variables, initialTimeOut,
                pollIntervall, backoff, retries, fresh);
    }

    public AttributeSubscription {
        requireValidName(attributeNameReference);
    }

}
