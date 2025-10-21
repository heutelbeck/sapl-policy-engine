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
package io.sapl.attributes.broker.impl;

import io.sapl.attributes.broker.api.AttributeKey;
import io.sapl.attributes.broker.api.AttributeStorage;
import io.sapl.attributes.broker.api.PersistedAttribute;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage using ConcurrentHashMap.
 * <p>
 * No actual persistence - all data is lost on restart.
 * Wraps synchronous operations in Mono for interface compliance.
 * <p>
 * This is the default implementation providing the same behavior
 * as the original repository without any file or database dependencies.
 */
public class HeapAttributeStorage implements AttributeStorage {

    private final ConcurrentHashMap<AttributeKey, PersistedAttribute> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<PersistedAttribute> get(AttributeKey key) {
        return Mono.justOrEmpty(storage.get(key));
    }

    @Override
    public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
        return Mono.fromRunnable(() -> storage.put(key, value));
    }

    @Override
    public Mono<Void> remove(AttributeKey key) {
        return Mono.fromRunnable(() -> storage.remove(key));
    }

    @Override
    public Flux<AttributeKey> getAllKeys() {
        return Flux.fromIterable(new ArrayList<>(storage.keySet()));
    }

    @Override
    public Mono<Integer> size() {
        return Mono.just(storage.size());
    }
}
