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
package io.sapl.attributes.store;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import lombok.NonNull;

import java.util.List;

import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * Metadata describing a {@link StreamAttributeFinder}'s signature
 * and implementation. The store uses this record to detect spec
 * collisions at load time ({@link #collidesWith}) and to match
 * incoming invocations to a serving spec ({@link #matches}).
 *
 * @param namespace the PIP namespace (e.g. "time")
 * @param attributeName the attribute name (e.g. "now")
 * @param isEnvironmentAttribute true if this is an environment
 * attribute (no entity parameter)
 * @param parameterTypes fixed parameter types (entity excluded)
 * @param varArgsParameterType type of varargs parameter, or null
 * @param attributeFinder the implementation
 */
public record StreamAttributeFinderSpecification(
        @NonNull String namespace,
        @NonNull String attributeName,
        boolean isEnvironmentAttribute,
        @NonNull List<Class<? extends Value>> parameterTypes,
        Class<? extends Value> varArgsParameterType,
        @NonNull StreamAttributeFinder attributeFinder) {

    public StreamAttributeFinderSpecification {
        requireValidName(fullyQualifiedName());
    }

    /**
     * Checks if this attribute finder accepts a variable number of
     * arguments.
     *
     * @return true if {@code varArgsParameterType} is not null
     */
    public boolean hasVariableNumberOfArguments() {
        return varArgsParameterType != null;
    }

    /**
     * Checks if this specification would collide with another
     * specification.
     *
     * @param other another specification
     * @return true if both specifications would match the same
     * invocations
     */
    public boolean collidesWith(StreamAttributeFinderSpecification other) {
        if (!fullyQualifiedName().equals(other.fullyQualifiedName())
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() && other.hasVariableNumberOfArguments())
                || parameterTypes.size() == other.parameterTypes.size();
    }

    /**
     * Determines how well this specification matches an invocation.
     *
     * @param invocation the attribute finder invocation
     * @return {@link Match#EXACT_MATCH} if arguments equal parameters,
     * {@link Match#VARARGS_MATCH} if varargs and enough arguments,
     * {@link Match#NO_MATCH} otherwise
     */
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

    /**
     * Returns the fully qualified attribute name in the form
     * {@code namespace.attributeName}.
     *
     * @return the fully qualified name
     */
    public String fullyQualifiedName() {
        return namespace + '.' + attributeName;
    }
}
