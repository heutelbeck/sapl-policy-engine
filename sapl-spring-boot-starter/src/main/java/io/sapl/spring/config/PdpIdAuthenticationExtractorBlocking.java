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

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import org.springframework.security.core.Authentication;

/**
 * Synchronous companion to {@link PdpIdAuthenticationExtractor} for
 * use on servlet-stack threads where {@code SecurityContextHolder} is
 * available and Reactor is not appropriate.
 * <p>
 * Applications wishing to support tenant routing on the servlet path
 * register a bean of this type. The default implementation registered
 * by Spring autoconfig returns
 * {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}
 * unconditionally.
 */
@FunctionalInterface
public interface PdpIdAuthenticationExtractorBlocking {

    /**
     * Extracts the PDP identifier from the given authentication.
     *
     * @param authentication the current authentication, possibly
     * {@code null} if no user is authenticated
     * @return the PDP identifier
     */
    String extractPdpId(Authentication authentication);
}
