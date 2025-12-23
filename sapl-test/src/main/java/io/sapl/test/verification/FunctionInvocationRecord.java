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
package io.sapl.test.verification;

import io.sapl.api.model.Value;

import java.util.List;

/**
 * Record of a function invocation for verification purposes.
 *
 * @param functionName fully qualified function name
 * @param arguments the arguments passed to the function
 * @param sequenceNumber invocation order (for ordering verification)
 */
public record FunctionInvocationRecord(String functionName, List<Value> arguments, long sequenceNumber) {

    public FunctionInvocationRecord {
        arguments = List.copyOf(arguments);
    }

    @Override
    public String toString() {
        return "%s(%s)".formatted(functionName, formatArguments());
    }

    private String formatArguments() {
        if (arguments.isEmpty()) {
            return "";
        }
        return arguments.stream().map(Value::toString).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
