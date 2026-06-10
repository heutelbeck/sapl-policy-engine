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
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import io.sapl.spring.tenant.DefaultBlockingTenantResolver;
import io.sapl.spring.tenant.DefaultReactiveTenantResolver;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers default {@link ReactiveTenantResolver} and
 * {@link BlockingTenantResolver} beans. Applications override
 * either by providing their own bean of the matching type.
 * <p>
 * The default reactive resolver reads the tenant id from the Reactor Context
 * (populated by {@link PdpIdWebFilter}). The
 * default blocking resolver reads the id from {@code SecurityContextHolder} via
 * a configured
 * {@link PdpIdAuthenticationExtractorBlocking}; without one, every call
 * resolves to
 * {@link StreamingPolicyDecisionPoint#DEFAULT_PDP_ID}.
 */
@AutoConfiguration
public class TenantResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ReactiveTenantResolver reactiveTenantResolver() {
        return new DefaultReactiveTenantResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    PdpIdAuthenticationExtractorBlocking pdpIdAuthenticationExtractorBlocking() {
        return authentication -> StreamingPolicyDecisionPoint.DEFAULT_PDP_ID;
    }

    @Bean
    @ConditionalOnMissingBean
    BlockingTenantResolver blockingTenantResolver(PdpIdAuthenticationExtractorBlocking extractor) {
        return new DefaultBlockingTenantResolver(extractor);
    }
}
