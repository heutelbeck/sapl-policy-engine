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
import io.sapl.api.shared.Match;
import lombok.NonNull;

import java.util.List;

import static io.sapl.validation.NameValidator.requireValidName;

public record AttributeFinderSpecification(
        @NonNull String namespace,
        @NonNull String attributeName,
        boolean isEnvironmentAttribute,
        @NonNull List<Class<? extends Value>> parameterTypes,
        Class<? extends Value> varArgsParameterType,
        @NonNull AttributeFinder attributeFinder) {

    public AttributeFinderSpecification {
        requireValidName(fullyQualifiedName());
    }

    public boolean hasVariableNumberOfArguments() {
        return varArgsParameterType != null;
    }

    /**
     * @param other another specification
     * @return true, if the presence of the two specifications leads to
     * disambiguation in resolving PIP lookups.
     */
    public boolean collidesWith(AttributeFinderSpecification other) {
        if (!fullyQualifiedName().equals(other.fullyQualifiedName())
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() && other.hasVariableNumberOfArguments())
                || parameterTypes.size() == other.parameterTypes.size();
    }

    public Match matches(AttributeFinderInvocation invocation) {
        if (!invocation.attributeName().equals(fullyQualifiedName())
                || (isEnvironmentAttribute != invocation.isEnvironmentAttributeInvocation())) {
            return Match.NO_MATCH;
        }

        if (invocation.arguments().size() == parameterTypes.size()) {
            return Match.EXACT_MATCH;
        }

        if (hasVariableNumberOfArguments() && invocation.arguments().size() >= parameterTypes.size()) {
            return Match.VARARGS_MATCH;
        }

        return Match.NO_MATCH;
    }

    public String fullyQualifiedName() {
        return namespace + '.' + attributeName;
    }

}
