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
package io.sapl.reactive.api.tenant;

import io.sapl.reactive.api.pdp.PolicyDecisionPoint;
import reactor.core.publisher.Mono;

/**
 * Resolves the PDP tenant identifier for a reactive request. Reactive
 * components (WebFlux controllers, reactive PEPs) inject this resolver
 * and pass the resolved id to the PDP explicitly. The PDP itself does
 * not know about transport conventions.
 * <p>
 * Implementations may read the id from a Reactor Context populated
 * upstream by an HTTP filter, from request headers, from a JWT claim,
 * or from any other source. A missing tenant must resolve to
 * {@link PolicyDecisionPoint#DEFAULT_PDP_ID}.
 */
@FunctionalInterface
public interface ReactiveTenantResolver {

    /**
     * Returns the tenant identifier for the current reactive context.
     * Must never emit empty.
     *
     * @return the tenant identifier
     */
    Mono<String> resolve();
}
