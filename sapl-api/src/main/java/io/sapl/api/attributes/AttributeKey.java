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
import lombok.NonNull;

import java.util.List;

/**
 * Unique key identifying an attribute in storage.
 * <p>
 * Composed of entity (optional), attribute name, and arguments list. Two keys
 * are equal if all components are equal.
 */
public record AttributeKey(Value entity, @NonNull String attributeName, @NonNull List<Value> arguments) {
    /**
     * Creates an AttributeKey from an AttributeFinderInvocation.
     *
     * @param invocation
     * the invocation to extract key from
     *
     * @return the attribute key
     */
    public static AttributeKey of(AttributeFinderInvocation invocation) {
        return new AttributeKey(invocation.entity(), invocation.attributeName(), invocation.arguments());
    }
}
