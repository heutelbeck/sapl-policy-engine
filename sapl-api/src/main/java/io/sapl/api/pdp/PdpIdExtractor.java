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
package io.sapl.api.pdp;

import reactor.core.publisher.Mono;

/**
 * Extracts the PDP identifier from the current request context.
 * <p>
 * In multi-tenant deployments, different clients may be routed to different PDP
 * configurations based on their identity. This interface allows applications to
 * provide custom logic for determining which PDP configuration to use.
 * <p>
 * Implementations typically extract the PDP ID from the security context,
 * request headers, or other contextual information available in the reactive
 * pipeline.
 * <p>
 * Example implementation using Spring Security:
 *
 * <pre>{@code
 * @Bean
 * PdpIdExtractor pdpIdExtractor() {
 *     return () -> ReactiveSecurityContextHolder.getContext().map(ctx -> ctx.getAuthentication())
 *             .filter(auth -> auth instanceof SaplAuthenticationToken)
 *             .map(auth -> ((SaplAuthenticationToken) auth).getPdpId()).defaultIfEmpty("default");
 * }
 * }</pre>
 */
@FunctionalInterface
public interface PdpIdExtractor {

    /**
     * Extracts the PDP identifier from the current context.
     *
     * @return a Mono emitting the PDP ID, or "default" if not determinable
     */
    Mono<String> extract();

}
