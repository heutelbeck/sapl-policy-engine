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
package io.sapl.node.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Default {@link BlockingTenantResolver}: reads the current authentication
 * from {@code SecurityContextHolder}, delegates extraction of the PDP id to
 * a configured {@link PdpIdAuthenticationExtractorBlocking}, and falls back
 * to {@link ReactivePolicyDecisionPoint#DEFAULT_PDP_ID} when no
 * authentication or no extracted id is available.
 */
@RequiredArgsConstructor
public final class DefaultBlockingTenantResolver implements BlockingTenantResolver {

    private final PdpIdAuthenticationExtractorBlocking extractor;

    @Override
    public String resolve() {
        val securityContext = SecurityContextHolder.getContext();
        if (securityContext == null) {
            return ReactivePolicyDecisionPoint.DEFAULT_PDP_ID;
        }
        Authentication authentication = securityContext.getAuthentication();
        val            extracted      = extractor.extractPdpId(authentication);
        return extracted == null || extracted.isBlank() ? ReactivePolicyDecisionPoint.DEFAULT_PDP_ID : extracted;
    }
}
