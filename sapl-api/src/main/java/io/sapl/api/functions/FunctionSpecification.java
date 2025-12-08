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
package io.sapl.api.functions;

import io.sapl.api.model.Value;
import lombok.NonNull;

import java.util.List;
import java.util.function.Function;

import static io.sapl.api.shared.NameValidator.requireValidName;

public record FunctionSpecification(
        @NonNull String namespace,
        @NonNull String functionName,
        List<Class<? extends Value>> parameterTypes,
        Class<? extends Value> varArgsParameterType,
        Function<FunctionInvocation, Value> function) {

    public FunctionSpecification {
        requireValidName(functionName());
    }

    public int numberOfArguments() {
        return parameterTypes.size();
    }

    public boolean hasVariableNumberOfArguments() {
        return varArgsParameterType != null;
    }

    public boolean collidesWith(FunctionSpecification otherSpec) {
        return otherSpec.functionName().equals(functionName()) && numberOfArguments() == otherSpec.numberOfArguments()
                && hasVariableNumberOfArguments() == otherSpec.hasVariableNumberOfArguments();
    }

    public String functionName() {
        return namespace + '.' + functionName;
    }

}
