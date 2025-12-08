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
import io.sapl.api.shared.Match;

import java.util.List;

import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * A function invocation with PDP config ID, function name, and arguments.
 */
public record FunctionInvocation(String functionName, List<Value> arguments) {

    public FunctionInvocation {
        requireValidName(functionName);
        arguments = List.copyOf(arguments);
    }

    public int argumentCount() {
        return arguments.size();
    }

    public Match matches(FunctionSpecification spec) {
        if (!spec.functionName().equals(functionName())) {
            return Match.NO_MATCH;
        }

        if (arguments.size() == spec.numberOfArguments()) {
            return Match.EXACT_MATCH;
        }

        if (spec.hasVariableNumberOfArguments()) {
            return Match.VARARGS_MATCH;
        }

        return Match.NO_MATCH;
    }
}
