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
package io.sapl.attributes.store;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;

/**
 * Functional interface for a Stream-based Policy Information Point
 * attribute finder. Returns a {@link Stream} of {@link Value}; errors
 * are surfaced as {@link io.sapl.api.model.ErrorValue} elements rather
 * than thrown.
 */
@FunctionalInterface
public interface StreamAttributeFinder {

    /**
     * Invokes the attribute finder and returns its value stream.
     *
     * @param invocation the attribute finder invocation
     * @return a {@link Stream} emitting attribute values
     */
    Stream<Value> invoke(AttributeFinderInvocation invocation);
}
