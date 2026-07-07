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
package io.sapl.node.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.val;

/**
 * Pins the opt-in contract of the HTTP admission limits: an unconfigured
 * limit registers no filter at all, so the unlimited request path is
 * byte-identical to a node without the feature.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdpHttpEndpointConfiguration limit wiring")
class PdpHttpEndpointLimitWiringTests {

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistry;

    private final PdpHttpEndpointConfiguration sut = new PdpHttpEndpointConfiguration();

    @Test
    @DisplayName("unconfigured limits produce non-null no-op initializers so the servlet bootstrap accepts them")
    void whenLimitsUnconfiguredThenNoOpInitializersReturned() {
        assertThat(sut.sseStreamLimitFilter(0, meterRegistry)).isNotNull()
                .isNotInstanceOf(FilterRegistrationBean.class);
        assertThat(sut.requestRateLimitFilter(0, meterRegistry)).isNotNull()
                .isNotInstanceOf(FilterRegistrationBean.class);
    }

    @Test
    @DisplayName("a configured SSE stream cap registers the filter on the streaming routes only")
    void whenSseCapConfiguredThenFilterOnStreamingRoutes() {
        val registration = sut.sseStreamLimitFilter(100, meterRegistry);
        assertThat(registration).isInstanceOf(FilterRegistrationBean.class)
                .satisfies(r -> assertThat(((FilterRegistrationBean<?>) r).getUrlPatterns()).containsExactlyInAnyOrder(
                        "/api/pdp/decide", "/api/pdp/multi-decide", "/api/pdp/multi-decide-all"));
    }

    @Test
    @DisplayName("a configured request rate registers the filter on the unary routes only")
    void whenRateConfiguredThenFilterOnUnaryRoutes() {
        val registration = sut.requestRateLimitFilter(100, meterRegistry);
        assertThat(registration).isInstanceOf(FilterRegistrationBean.class)
                .satisfies(r -> assertThat(((FilterRegistrationBean<?>) r).getUrlPatterns())
                        .containsExactlyInAnyOrder("/api/pdp/decide-once", "/api/pdp/multi-decide-all-once"));
    }
}
