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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.broker.AttributeRepository;
import io.sapl.api.broker.AttributeStreamBroker;
import io.sapl.api.interpreter.Val;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InMemoryAttributeRepository implements AttributeRepository {

    private final AttributeStreamBroker broker;

    private record AttributeKey(JsonNode entity, String attributeName) {}

    private final Map<AttributeKey, Val> repository = new ConcurrentHashMap<>();;

    @Override
    public void publishAttribute(@NonNull JsonNode entity, @NonNull String attributeName, @NonNull Val attributeValue) {
        repository.put(new AttributeKey(entity, attributeName), attributeValue);
    }

    @Override
    public void publishEnvironmentAttribute(@NonNull JsonNode entity, @NonNull String attributeName,
            @NonNull Val attributeValue) {
        repository.put(new AttributeKey(null, attributeName), attributeValue);
    }

    @Override
    public void removeAttribute(@NonNull JsonNode entity, @NonNull String attributeName) {
        repository.remove(new AttributeKey(entity, attributeName));
    }

    @Override
    public void removeEnvironmentAttribute(@NonNull String attributeName) {
        repository.remove(new AttributeKey(null, attributeName));
    }

}
