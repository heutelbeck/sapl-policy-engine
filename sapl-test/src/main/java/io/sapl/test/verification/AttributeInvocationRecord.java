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
package io.sapl.test.verification;

import io.sapl.api.model.Value;

import java.util.List;

/**
 * Record of an attribute finder invocation for verification purposes.
 *
 * @param attributeName fully qualified attribute name
 * @param entity the entity value (null for environment attributes)
 * @param arguments the arguments passed to the attribute finder
 * @param sequenceNumber invocation order (for ordering verification)
 */
public record AttributeInvocationRecord(
        String attributeName,
        Value entity,
        List<Value> arguments,
        long sequenceNumber) {

    public AttributeInvocationRecord {
        arguments = List.copyOf(arguments);
    }

    /**
     * Returns true if this is an environment attribute invocation.
     */
    public boolean isEnvironmentAttribute() {
        return entity == null;
    }

    @Override
    public String toString() {
        if (isEnvironmentAttribute()) {
            return "|%s(%s)".formatted(attributeName, formatArguments());
        }
        return "%s.|%s(%s)".formatted(entity, attributeName, formatArguments());
    }

    private String formatArguments() {
        if (arguments.isEmpty()) {
            return "";
        }
        return arguments.stream().map(Value::toString).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
