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
package io.sapl.api.attributes;

import io.sapl.api.model.Value;
import reactor.core.publisher.Flux;

/**
 * Functional interface representing a Policy Information Point (PIP) attribute
 * finder.
 * <p>
 * An AttributeFinder provides external data to SAPL policies through reactive
 * streams. It is invoked when a policy
 * references an attribute (e.g., {@code <pip.attribute>} or
 * {@code entity.<pip.attribute>}).
 * <p>
 * <b>Implementation Requirements:</b>
 * <ul>
 * <li>Must return a Flux that emits Value objects</li>
 * <li>May emit multiple values over time (streaming attributes)</li>
 * <li>Should handle errors by emitting {@link Value#error(String)} instead of
 * propagating exceptions</li>
 * <li>Must respect the invocation parameters (entity, arguments, options)</li>
 * </ul>
 * <p>
 * <b>Lifecycle:</b>
 * <ul>
 * <li>Created via @Attribute annotated methods or manual registration</li>
 * <li>Invoked by the AttributeBroker when policies request the attribute</li>
 * <li>May be replaced during runtime (PIP hot-swapping)</li>
 * </ul>
 *
 * @see AttributeFinderInvocation
 * @see Attribute
 */
@FunctionalInterface
public interface AttributeFinder {

    /**
     * Invokes the attribute finder to retrieve attribute values.
     * <p>
     * This method is called by the AttributeBroker when a policy evaluates an
     * attribute expression. The implementation
     * should:
     * <ul>
     * <li>Use the entity from the invocation as the first parameter (if
     * applicable)</li>
     * <li>Extract additional arguments from the invocation</li>
     * <li>Apply options like polling intervals, timeouts, and retries (handled by
     * AttributeStream)</li>
     * <li>Return a reactive stream that may emit multiple values over time</li>
     * </ul>
     *
     * @param invocation
     * the attribute finder invocation containing entity, arguments, and metadata
     *
     * @return a Flux emitting attribute values; errors should be emitted as
     * {@link Value#error(String)}
     */
    Flux<Value> invoke(AttributeFinderInvocation invocation);
}
