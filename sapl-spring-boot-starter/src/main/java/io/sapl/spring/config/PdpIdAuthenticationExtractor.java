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
package io.sapl.spring.config;

import org.springframework.security.core.Authentication;

import io.sapl.api.pdp.MultiTenantPolicyDecisionPoint;
import reactor.core.publisher.Mono;

/**
 * Extracts the PDP identifier from the current user's authentication for
 * multi-tenant routing.
 * <p>
 * Implementations inspect the {@link Authentication} object and return the
 * tenant's PDP ID. When registered as a Spring bean, the SAPL auto-
 * configuration creates a WebFilter that writes the extracted PDP ID to the
 * Reactor Context, enabling automatic multi-tenant routing via
 * {@link MultiTenantPolicyDecisionPoint}.
 * <p>
 * Example for JWT-based tenancy:
 *
 * <pre>{@code
 * @Bean
 * PdpIdAuthenticationExtractor pdpIdExtractor() {
 *     return auth -> {
 *         if (auth instanceof JwtAuthenticationToken jwt) {
 *             return Mono.justOrEmpty(jwt.getToken().getClaimAsString("tenant_id"));
 *         }
 *         return Mono.just("default");
 *     };
 * }
 * }</pre>
 */
@FunctionalInterface
public interface PdpIdAuthenticationExtractor {

    /**
     * Extracts the PDP identifier from the given authentication.
     *
     * @param authentication the current user's authentication
     * @return a Mono emitting the PDP ID, or empty to fall back to
     * {@link MultiTenantPolicyDecisionPoint#DEFAULT_PDP_ID}
     */
    Mono<String> extractPdpId(Authentication authentication);

}
