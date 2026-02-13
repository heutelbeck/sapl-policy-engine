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
package io.sapl.node;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
import lombok.RequiredArgsConstructor;

/**
 * Registers the PDP metrics interceptor for Prometheus instrumentation.
 *
 * <p>
 * The enabled flag is read from
 * {@code io.sapl.pdp.embedded.metrics-enabled} at startup and passed to the
 * interceptor. When disabled, all interceptor callbacks are no-ops -- zero
 * cost at runtime.
 *
 * <p>
 * This is a runtime check, not a {@code @Conditional}, ensuring correct
 * behavior in both JVM and GraalVM native images.
 */
@Configuration
@RequiredArgsConstructor
class MetricsConfiguration {

    private final EmbeddedPDPProperties properties;

    @Bean
    VoteInterceptor metricsVoteInterceptor(MeterRegistry meterRegistry) {
        return new MetricsVoteInterceptor(properties.isMetricsEnabled(), meterRegistry);
    }

}
