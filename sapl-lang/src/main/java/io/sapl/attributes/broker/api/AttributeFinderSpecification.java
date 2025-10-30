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
package io.sapl.attributes.broker.api;

import io.sapl.validation.Validator;
import lombok.NonNull;

import java.util.List;

import static io.sapl.validation.NameValidator.requireValidName;

public record AttributeFinderSpecification(
        @NonNull String namespace,
        @NonNull String attributeName,
        boolean isEnvironmentAttribute,
        int numberOfArguments,
        boolean takesVariables,
        @NonNull Validator entityValidator,
        @NonNull List<Validator> parameterValidators) {

    public enum Match {
        NO_MATCH,
        EXACT_MATCH,
        VARARGS_MATCH,
        CATCH_ALL_MATCH,
    }

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public AttributeFinderSpecification {
        requireValidName(fullyQualifiedName());
    }

    public boolean hasVariableNumberOfArguments() {
        return numberOfArguments == HAS_VARIABLE_NUMBER_OF_ARGUMENTS;
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
                || numberOfArguments == other.numberOfArguments;
    }

    public Match matches(AttributeFinderInvocation invocation) {
        if (!invocation.attributeName().equals(fullyQualifiedName())
                || (isEnvironmentAttribute != invocation.isEnvironmentAttributeInvocation())) {
            return Match.NO_MATCH;
        }

        if (invocation.arguments().size() == numberOfArguments) {
            return Match.EXACT_MATCH;
        }

        if (hasVariableNumberOfArguments()) {
            return Match.VARARGS_MATCH;
        }

        return Match.NO_MATCH;
    }

    public String fullyQualifiedName() {
        return namespace + '.' + attributeName;
    }

}
