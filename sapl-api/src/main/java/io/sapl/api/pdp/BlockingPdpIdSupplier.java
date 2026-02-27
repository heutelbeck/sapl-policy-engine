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

/**
 * Supplies the PDP identifier for blocking (synchronous) authorization
 * decisions.
 * <p>
 * In multi-tenant deployments, different clients may be routed to different PDP
 * configurations based on their identity. This interface allows applications to
 * provide custom logic for determining which PDP configuration to use in
 * blocking contexts such as {@code @PreEnforce}, {@code @PostEnforce}, and
 * {@code AuthorizationManager} implementations.
 * <p>
 * For reactive (WebFlux) contexts, use {@link PdpIdExtractor} instead.
 * <p>
 * Implementations typically extract the PDP ID from the Spring Security context
 * or other thread-local information available in the servlet request thread.
 * <p>
 * Example implementation using Spring Security:
 *
 * <pre>{@code
 * @Bean
 * BlockingPdpIdSupplier blockingPdpIdSupplier() {
 *     return () -> {
 *         var auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth instanceof SaplAuthenticationToken saplAuth) {
 *             return saplAuth.getPdpId();
 *         }
 *         return "default";
 *     };
 * }
 * }</pre>
 */
@FunctionalInterface
public interface BlockingPdpIdSupplier {

    /**
     * Returns the PDP identifier for the current blocking context.
     *
     * @return the PDP ID, never {@code null}
     */
    String get();

}
