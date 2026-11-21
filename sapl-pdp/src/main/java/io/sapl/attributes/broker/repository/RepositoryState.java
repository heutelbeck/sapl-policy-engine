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
package io.sapl.attributes.broker.repository;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.NonNull;

/**
 * Complete immutable state of one routed source. Revisions are ordered only
 * within the lifetime of an observation. A newer state subsumes every older one.
 *
 * @param revision source revision
 * @param bindings all current bindings in the observed tenant
 * @param unavailable source failure, if its current state cannot be established
 */
public record RepositoryState(
        long revision,
        @NonNull Map<RepositoryKey, Binding> bindings,
        @Nullable ErrorValue unavailable) {

    private static final Binding ABSENT = new Binding(-1L, Value.UNDEFINED);

    public RepositoryState {
        bindings = Map.copyOf(bindings);
    }

    /**
     * @param generation revision at which the value was last bound
     * @param value value visible to the policy
     */
    public record Binding(long generation, @NonNull Value value) {}

    /**
     * @param key repository key in this observation
     * @return its binding, confirmed absence, or the source failure
     */
    public Binding binding(RepositoryKey key) {
        if (unavailable != null) {
            return new Binding(revision, unavailable);
        }
        return bindings.getOrDefault(key, ABSENT);
    }

    /**
     * @param key repository key in this observation
     * @return the visible SAPL value
     */
    public Value value(RepositoryKey key) {
        return binding(key).value();
    }
}
