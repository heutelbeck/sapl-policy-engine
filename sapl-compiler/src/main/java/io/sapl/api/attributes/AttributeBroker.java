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
 * Central broker for attribute finder invocations in the SAPL policy evaluation
 * process.
 * <p>
 * The AttributeBroker routes attribute requests to matching PIP implementations
 * based on attribute name and signature. Resolution follows this priority:
 * <ol>
 * <li>Exact parameter match in registered PIPs</li>
 * <li>Varargs parameter match in registered PIPs</li>
 * <li>AttributeRepository fallback (if PIPs exist for that name but none
 * match)</li>
 * <li>Error stream (if no PIP registered for that attribute name)</li>
 * </ol>
 * <p>
 * Additional capabilities:
 * <ul>
 * <li>Stream caching and reuse based on invocation parameters</li>
 * <li>PIP hot-swapping during runtime</li>
 * <li>Grace periods for stream lifecycle management</li>
 * <li>Fresh attribute requests (bypassing cache)</li>
 * </ul>
 *
 * @see AttributeFinderInvocation
 * @see AttributeFinder
 * @see AttributeRepository
 */
public interface AttributeBroker {

    /**
     * Requests an attribute stream for the given invocation.
     * <p>
     * The broker searches for a matching PIP by attribute name and signature. If
     * PIPs are registered for that name but none match the signature, the request
     * falls back to the AttributeRepository. If no PIPs are registered for that
     * attribute name, returns an error stream.
     * <p>
     * Depending on the {@code fresh} flag in the invocation, the broker either:
     * <ul>
     * <li>Returns a cached stream (if {@code fresh=false} and one exists)</li>
     * <li>Creates a new stream (if {@code fresh=true} or no cached stream
     * exists)</li>
     * </ul>
     *
     * @param invocation the attribute finder invocation containing the attribute
     * name, entity, arguments, and options
     * @return a Flux emitting attribute values from a PIP, repository, or error
     */
    Flux<Value> attributeStream(AttributeFinderInvocation invocation);
}
