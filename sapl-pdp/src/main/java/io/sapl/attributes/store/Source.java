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

import java.util.Optional;

/**
 * Internal source-chain abstraction for the attribute store. A
 * {@code Source} answers, for a given invocation, whether it can
 * supply values and, if so, returns a {@link SourceBinding}
 * carrying the value stream and an opaque tag the store uses for
 * provenance tracking. Order of consultation in the chain is
 * configured by the store; the first source whose
 * {@link #open(AttributeFinderInvocation)} returns a non-empty
 * {@code Optional} wins for that invocation.
 */
@FunctionalInterface
interface Source {

    /**
     * Open a value stream for the given invocation, if this source
     * serves it. Returning empty means "I do not serve this
     * invocation; try the next source in the chain."
     */
    Optional<SourceBinding> open(AttributeFinderInvocation invocation);
}
