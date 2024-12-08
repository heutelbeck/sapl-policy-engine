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

import java.util.List;

import io.sapl.validation.Validator;
import lombok.NonNull;

public record AttributeFinderSpecification(@NonNull String fullyQualifiedAttributeName, boolean isEnvironmentAttribute,
        int numberOfArguments, boolean takesVariables, @NonNull Validator entityValidator,
        @NonNull List<Validator> parameterValidators) {

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public AttributeFinderSpecification {
        requireValidName(fullyQualifiedAttributeName);
    }

    public boolean hasVariableNumberOfArguments() {
        return numberOfArguments == HAS_VARIABLE_NUMBER_OF_ARGUMENTS;
    }

    public boolean matches(AttributeFinderInvocation invocation) {
        // @formatter:off
        return    (invocation.fullyQualifiedAttributeName().equals(fullyQualifiedAttributeName))
               && (null != invocation.entity() ^ isEnvironmentAttribute)
               && (invocation.arguments().size() == numberOfArguments || hasVariableNumberOfArguments());
        // @formatter:on
    }

    /**
     * @param other another specification
     * @return true, if the presence of the two specifications leads to
     * disambiguates in resolving PIP lookups.
     */
    public boolean collidesWith(AttributeFinderSpecification other) {
        if (!fullyQualifiedAttributeName.equals(other.fullyQualifiedAttributeName)
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() || other.hasVariableNumberOfArguments())
                || numberOfArguments == other.numberOfArguments;
    }

}
