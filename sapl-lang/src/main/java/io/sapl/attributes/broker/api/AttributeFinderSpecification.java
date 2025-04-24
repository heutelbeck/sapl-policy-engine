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

import static io.sapl.validation.NameValidator.requireValidName;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.validation.Validator;
import lombok.NonNull;

public record AttributeFinderSpecification(@NonNull String fullyQualifiedAttributeName, boolean isEnvironmentAttribute,
        int numberOfArguments, boolean takesVariables, @NonNull Validator entityValidator,
        @NonNull List<Validator> parameterValidators, JsonNode attributeSchema) {

    public enum Match {
        NO_MATCH, EXACT_MATCH, VARARGS_MATCH
    }

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public AttributeFinderSpecification {
        requireValidName(fullyQualifiedAttributeName);
    }

    public boolean hasVariableNumberOfArguments() {
        return numberOfArguments == HAS_VARIABLE_NUMBER_OF_ARGUMENTS;
    }

    public Match matches(AttributeFinderInvocation invocation) {
        // @formatter:off
        if(!invocation.fullyQualifiedAttributeName().equals(fullyQualifiedAttributeName) || (isEnvironmentAttribute && null != invocation.entity())) {
            return Match.NO_MATCH;            
        }
        
        if (invocation.arguments().size() == numberOfArguments) {
            return Match.EXACT_MATCH;
        }
        
        if(hasVariableNumberOfArguments()) {
            return Match.VARARGS_MATCH;
        }

        return Match.NO_MATCH;            
        // @formatter:on
    }

    /**
     * @param other another specification
     * @return true, if the presence of the two specifications leads to
     * disambiguates in resolving PIP lookups.
     */
    public boolean collidesWith(AttributeFinderSpecification other) {
        System.out.println("This: " + this);
        System.out.println("That: " + other);
        System.out.println("Name match: " + other.fullyQualifiedAttributeName().equals(fullyQualifiedAttributeName));
        System.out.println("Type match: " + (other.isEnvironmentAttribute() && isEnvironmentAttribute));
        System.out.println(
                "Arguments match: " + (other.numberOfArguments == numberOfArguments || hasVariableNumberOfArguments()));
        if (!fullyQualifiedAttributeName.equals(other.fullyQualifiedAttributeName)
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() && other.hasVariableNumberOfArguments())
                || numberOfArguments == other.numberOfArguments;
    }

    public String getCodeTemplate(String alias) {
        // TODO Auto-generated method stub
        return "UNIMPLEMENTED TEMPLATE CREATION";
    }

}
