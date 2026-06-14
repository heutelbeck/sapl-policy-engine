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
package io.sapl.node.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;

/**
 * Registers the PDP metrics interceptor for Prometheus instrumentation, but
 * only when {@code io.sapl.pdp.embedded.metrics-enabled} is set.
 *
 * <p>
 * When disabled the bean method returns {@code null}, so no
 * {@link io.sapl.api.pdp.DecisionInterceptor} is registered and the PDP takes
 * its no-interceptor fast path that produces no per-decision timestamp. A
 * registered interceptor, even a no-op one, would still force the PDP onto the
 * traced path (the timestamp is computed before interceptor dispatch), so
 * leaving the metrics interceptor inactive is not enough; it must be absent.
 *
 * <p>
 * The decision is a runtime check (a {@code null} return), not a
 * {@code @Conditional}, so it stays correct in GraalVM native images, where
 * {@code @Conditional} is resolved at build time.
 */
@Configuration
class MetricsConfiguration {

    private final EmbeddedPDPProperties properties;

    MetricsConfiguration(EmbeddedPDPProperties properties) {
        this.properties = properties;
    }

    @Bean
    PdpMetricsCollector pdpMetricsCollector(MeterRegistry meterRegistry) {
        if (!properties.isMetricsEnabled()) {
            return null;
        }
        return new PdpMetricsCollector(true, meterRegistry);
    }

}
