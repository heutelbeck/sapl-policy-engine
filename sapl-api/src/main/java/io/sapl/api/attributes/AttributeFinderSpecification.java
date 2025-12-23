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

import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import lombok.NonNull;

import java.util.List;

import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * Metadata describing an attribute finder's signature and implementation.
 * <p>
 * An AttributeFinderSpecification defines the contract for an attribute finder,
 * including its fully qualified name,
 * parameter types, and whether it accepts a variable number of arguments
 * (varargs). The AttributeBroker uses
 * specifications to:
 * <ul>
 * <li>Match invocations to the correct PIP implementation</li>
 * <li>Detect collisions during PIP registration</li>
 * <li>Provide type information for validation</li>
 * </ul>
 * <p>
 * <b>Matching Rules:</b>
 * <ul>
 * <li><b>EXACT_MATCH</b>: Attribute name matches AND argument count equals
 * parameter count</li>
 * <li><b>VARARGS_MATCH</b>: Has varargs AND argument count >= fixed parameter
 * count</li>
 * <li><b>NO_MATCH</b>: Name doesn't match OR argument/parameter count
 * mismatch</li>
 * </ul>
 * <p>
 * <b>Collision Detection:</b> Two specifications collide if they have the same
 * fully qualified name and would match the
 * same invocations (either both have varargs, or both have the same parameter
 * count).
 *
 * @param namespace
 * the PIP namespace (e.g., "time")
 * @param attributeName
 * the attribute name (e.g., "now")
 * @param isEnvironmentAttribute
 * true if this is an environment attribute (no entity parameter)
 * @param parameterTypes
 * list of fixed parameter types (entity excluded)
 * @param varArgsParameterType
 * type of varargs parameter, or null if no varargs
 * @param attributeFinder
 * the actual implementation
 *
 * @see AttributeBroker
 * @see Attribute
 */
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

    /**
     * Checks if this attribute finder accepts a variable number of arguments.
     *
     * @return true if varArgsParameterType is not null
     */
    public boolean hasVariableNumberOfArguments() {
        return varArgsParameterType != null;
    }

    /**
     * Checks if this specification would collide with another specification.
     * <p>
     * Two specifications collide if they have the same fully qualified name and
     * would match the same invocations,
     * making it ambiguous which one to invoke. Collisions are detected during PIP
     * registration and cause
     * AttributeBrokerException.
     * <p>
     * Collision conditions:
     * <ul>
     * <li>Different names or entity types -> no collision</li>
     * <li>Both have varargs -> collision (can't disambiguate)</li>
     * <li>Same parameter count -> collision (same signature)</li>
     * <li>Different parameter counts without varargs -> no collision</li>
     * </ul>
     *
     * @param other
     * another specification
     *
     * @return true if the presence of both specifications leads to ambiguity in
     * resolving PIP lookups
     */
    public boolean collidesWith(AttributeFinderSpecification other) {
        if (!fullyQualifiedName().equals(other.fullyQualifiedName())
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() && other.hasVariableNumberOfArguments())
                || parameterTypes.size() == other.parameterTypes.size();
    }

    /**
     * Determines how well this specification matches an invocation.
     * <p>
     * The matching algorithm checks:
     * <ol>
     * <li>Attribute name must match exactly</li>
     * <li>Environment attribute flag must match</li>
     * <li>Argument count must be compatible with parameter count</li>
     * </ol>
     * <p>
     * Match quality affects which PIP is selected when multiple PIPs have the same
     * name:
     * <ul>
     * <li>EXACT_MATCH is preferred over VARARGS_MATCH</li>
     * <li>VARARGS_MATCH is only used if no EXACT_MATCH exists</li>
     * </ul>
     *
     * @param invocation
     * the attribute finder invocation to match against
     *
     * @return Match.EXACT_MATCH if arguments equal parameters, Match.VARARGS_MATCH
     * if varargs and enough arguments,
     * Match.NO_MATCH otherwise
     *
     * @see io.sapl.api.shared.Match
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
     * Returns the fully qualified attribute name.
     * <p>
     * Format: {@code namespace.attributeName} (e.g., "time.now", "user.role").
     *
     * @return the fully qualified name
     */
    public String fullyQualifiedName() {
        return namespace + '.' + attributeName;
    }

}
