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
package io.sapl.broker.impl;

import static io.sapl.broker.impl.NameValidator.requireValidName;
import java.lang.annotation.Annotation;
import java.util.List;

import lombok.NonNull;

public record PolicyInformationPointSpecification(@NonNull String fullyQualifiedAttributeName,
        boolean isEnvironmentAttribute, int numberOfArguments, boolean takesVariables,
        @NonNull List<Annotation> entityValidators, @NonNull List<List<Annotation>> parameterValidators) {

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public PolicyInformationPointSpecification {
        requireValidName(fullyQualifiedAttributeName);
    }

    public boolean hasVariableNumberOfArguments() {
        return numberOfArguments == HAS_VARIABLE_NUMBER_OF_ARGUMENTS;
    }

    public boolean matches(PolicyInformationPointInvocation invocation) {
        // @formatter:off
        return    (invocation.fullyQualifiedAttributeName().equals(fullyQualifiedAttributeName))
               && (null != invocation.entity() ^ isEnvironmentAttribute)
               && (invocation.arguments().size() == numberOfArguments || hasVariableNumberOfArguments());
        // @formatter:on
    }

    /**
     * @param other another specification
     * @return true, if the presence of the two specifications leads to
     *         disambiguates in resolving PIP lookups.
     */
    public boolean collidesWith(PolicyInformationPointSpecification other) {
        if (!fullyQualifiedAttributeName.equals(other.fullyQualifiedAttributeName)
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() || other.hasVariableNumberOfArguments())
                || numberOfArguments == other.numberOfArguments;
    }

}
